use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode, header},
    response::{IntoResponse, Response},
};
use std::collections::HashMap;
use std::sync::Arc;

use crate::AppState;
use crate::processing::{self, CropRect, FocalPointParam, ImageParams, OutputFormat, ResizeMode};
use crate::signing;

/// Handle image requests.
///
/// URL format: /image/{file_uri}/{filename}.{ext}?w=&h=&m=&q=&c=&fp=&rot=&flipv=&fliph=&sig_key=sig_value
///
/// The `file_uri` is the storage path (e.g., "content/host/2024/01/uuid.jpg").
/// desk-api resolves the content ID to this path and signs the URL.
pub async fn handle_image(
    State(state): State<Arc<AppState>>,
    axum::extract::Path(path): axum::extract::Path<String>,
    Query(query): Query<HashMap<String, String>>,
) -> Response {
    // Validate HMAC signature
    let params_vec: Vec<(String, String)> = query.iter().map(|(k, v)| (k.clone(), v.clone())).collect();
    if !signing::validate_signature(
        &path,
        &params_vec,
        &state.config.signing.secret,
        state.config.signing.signature_length,
    ) {
        return (StatusCode::FORBIDDEN, "Invalid signature").into_response();
    }

    // Parse the path: everything before the last '/' segment is the file URI
    // Format: {space}/{host}/{date_path}/{uuid.ext}/{friendly_name.ext}
    // The friendly name is the last segment, file URI is everything before it
    let (file_uri, filename) = match path.rsplit_once('/') {
        Some((uri, name)) => (uri, name),
        None => return (StatusCode::BAD_REQUEST, "Invalid path").into_response(),
    };

    // Determine output format from filename extension
    let output_format = filename
        .rsplit_once('.')
        .and_then(|(_, ext)| OutputFormat::from_extension(ext));

    // Parse query parameters into ImageParams
    let params = ImageParams {
        width: query.get("w").and_then(|v| v.parse().ok()),
        height: query.get("h").and_then(|v| v.parse().ok()),
        mode: query
            .get("m")
            .map(|v| ResizeMode::from_str(v))
            .unwrap_or(ResizeMode::Fit),
        quality: query
            .get("q")
            .and_then(|v| v.parse().ok())
            .unwrap_or(state.config.processing.default_quality),
        crop: parse_crop(query.get("c").map(|s| s.as_str())),
        rotation: query.get("rot").and_then(|v| v.parse().ok()),
        flip_vertical: query.get("flipv").map(|v| v == "1" || v == "true").unwrap_or(false),
        flip_horizontal: query.get("fliph").map(|v| v == "1" || v == "true").unwrap_or(false),
        focal_point: parse_focal_point(query.get("fp").map(|s| s.as_str())),
        output_format,
    };

    // Check cache
    let cache_key = format!("{}?{}", path, query_to_sorted_string(&query));
    if let Some(ref cache) = state.cache {
        if let Some((data, content_type)) = cache.get(&cache_key) {
            let mut headers = HeaderMap::new();
            headers.insert(header::CONTENT_TYPE, content_type.parse().unwrap());
            headers.insert(header::CACHE_CONTROL, "public, max-age=31536000".parse().unwrap());
            headers.insert("X-Cache", "HIT".parse().unwrap());
            return (StatusCode::OK, headers, data).into_response();
        }
    }

    // Fetch original image from storage
    let raw_bytes = match state.storage.get_file(file_uri).await {
        Ok(bytes) => bytes,
        Err(e) => {
            tracing::warn!("File not found: {} — {}", file_uri, e);
            return (StatusCode::NOT_FOUND, "Image not found").into_response();
        }
    };

    // Process image
    let (output_bytes, format) = match processing::process_image(
        &raw_bytes,
        &params,
        state.config.processing.max_width,
        state.config.processing.max_height,
    ) {
        Ok(result) => result,
        Err(e) => {
            tracing::error!("Image processing failed: {}", e);
            return (StatusCode::INTERNAL_SERVER_ERROR, "Processing failed").into_response();
        }
    };

    // Store in cache
    if let Some(ref cache) = state.cache {
        cache.put(cache_key, output_bytes.clone(), format.content_type().to_string());
    }

    let mut headers = HeaderMap::new();
    headers.insert(header::CONTENT_TYPE, format.content_type().parse().unwrap());
    headers.insert(header::CACHE_CONTROL, "public, max-age=31536000".parse().unwrap());
    headers.insert("X-Cache", "MISS".parse().unwrap());

    (StatusCode::OK, headers, output_bytes).into_response()
}

/// Parse crop rectangle from "x,y,w,h" format
fn parse_crop(value: Option<&str>) -> Option<CropRect> {
    let s = value?;
    let parts: Vec<u32> = s.split(',').filter_map(|p| p.trim().parse().ok()).collect();
    if parts.len() == 4 {
        Some(CropRect {
            x: parts[0],
            y: parts[1],
            width: parts[2],
            height: parts[3],
        })
    } else {
        None
    }
}

/// Parse focal point from "x,y,zoom" format (values 0.0-1.0 for x,y)
fn parse_focal_point(value: Option<&str>) -> Option<FocalPointParam> {
    let s = value?;
    let parts: Vec<f64> = s.split(',').filter_map(|p| p.trim().parse().ok()).collect();
    if parts.len() >= 2 {
        Some(FocalPointParam {
            x: parts[0],
            y: parts[1],
            zoom: parts.get(2).copied().unwrap_or(1.0),
        })
    } else {
        None
    }
}

fn query_to_sorted_string(query: &HashMap<String, String>) -> String {
    let mut pairs: Vec<_> = query.iter().collect();
    pairs.sort_by_key(|(k, _)| k.as_str());
    pairs
        .iter()
        .map(|(k, v)| format!("{}={}", k, v))
        .collect::<Vec<_>>()
        .join("&")
}
