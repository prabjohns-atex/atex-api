use axum::{
    extract::{Query, State},
    http::{HeaderMap, StatusCode, header},
    response::{IntoResponse, Response},
};
use std::collections::HashMap;
use std::sync::Arc;

use crate::AppState;
use crate::processing::{self, AspectRatioParam, CropRect, FocalPointParam, ImageParams, OutputFormat, ResizeMode};
use crate::signing;

/// Handle image requests.
///
/// URL format: /image/{file_uri}/{filename}.{ext}?w=&h=&m=&q=&c=&fp=&rot=&flipv=&fliph=&a=&sig
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
    let (file_uri, filename) = match path.rsplit_once('/') {
        Some((uri, name)) => (uri, name),
        None => return (StatusCode::BAD_REQUEST, "Invalid path").into_response(),
    };

    // Fetch original image from storage
    let raw_bytes = match state.storage.get_file(file_uri).await {
        Ok(bytes) => bytes,
        Err(e) => {
            tracing::warn!("File not found: {} — {}", file_uri, e);
            return (StatusCode::NOT_FOUND, "Image not found").into_response();
        }
    };

    // SVG passthrough — serve raw without processing
    let is_svg = filename.ends_with(".svg")
        || filename.ends_with(".svgz")
        || (raw_bytes.len() > 5 && &raw_bytes[..5] == b"<?xml")
        || (raw_bytes.len() > 4 && &raw_bytes[..4] == b"<svg");
    if is_svg {
        let mut headers = HeaderMap::new();
        headers.insert(header::CONTENT_TYPE, "image/svg+xml".parse().unwrap());
        headers.insert(header::CACHE_CONTROL, "public, max-age=31536000".parse().unwrap());
        return (StatusCode::OK, headers, raw_bytes).into_response();
    }

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
        aspect_ratio: parse_aspect_ratio(query.get("a").map(|s| s.as_str())),
        rotation: query.get("rot").and_then(|v| v.parse().ok()),
        flip_vertical: query.get("flipv").map(|v| v == "1" || v == "true").unwrap_or(false),
        flip_horizontal: query.get("fliph").map(|v| v == "1" || v == "true").unwrap_or(false),
        focal_point: parse_focal_point(query.get("fp").map(|s| s.as_str())),
        output_format,
    };

    // Get original dimensions from query (passed by desk-api from ImageInfoAspectBean)
    let orig_width = query.get("ow").and_then(|v| v.parse::<u32>().ok());
    let orig_height = query.get("oh").and_then(|v| v.parse::<u32>().ok());

    // Process image
    let (output_bytes, format, rendered_width, rendered_height) = match processing::process_image(
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

    let mut headers = HeaderMap::new();
    headers.insert(header::CONTENT_TYPE, format.content_type().parse().unwrap());
    headers.insert(header::CACHE_CONTROL, "public, max-age=31536000".parse().unwrap());

    // Original image dimensions (from desk-api)
    if let Some(w) = orig_width {
        headers.insert("X-Original-Image-Width", w.to_string().parse().unwrap());
    }
    if let Some(h) = orig_height {
        headers.insert("X-Original-Image-Height", h.to_string().parse().unwrap());
    }

    // Rendered image dimensions
    headers.insert("X-Rendered-Image-Width", rendered_width.to_string().parse().unwrap());
    headers.insert("X-Rendered-Image-Height", rendered_height.to_string().parse().unwrap());

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

/// Parse aspect ratio from "w:h" format (e.g., "16:9", "3:2")
fn parse_aspect_ratio(value: Option<&str>) -> Option<AspectRatioParam> {
    let s = value?;
    let parts: Vec<u32> = s.split(':').filter_map(|p| p.trim().parse().ok()).collect();
    if parts.len() == 2 && parts[0] > 0 && parts[1] > 0 {
        Some(AspectRatioParam {
            width: parts[0],
            height: parts[1],
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
