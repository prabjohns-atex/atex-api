use fast_image_resize::{images::Image as FirImage, Resizer};
use image::{DynamicImage, GenericImageView, ImageFormat, ImageReader};
use lru::LruCache;
use std::io::Cursor;
use std::num::NonZeroUsize;
use std::sync::Mutex;

/// Resize mode matching Polopoly's ResizeMode enum.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ResizeMode {
    /// Resize to fit inside the box, preserving aspect ratio
    Fit,
    /// Crop to fill the box, then resize
    Fill,
}

impl ResizeMode {
    pub fn from_str(s: &str) -> Self {
        match s.to_uppercase().as_str() {
            "FILL" => ResizeMode::Fill,
            _ => ResizeMode::Fit,
        }
    }
}

/// Image processing parameters extracted from query string.
#[derive(Debug, Clone)]
pub struct ImageParams {
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub mode: ResizeMode,
    pub quality: f32,
    pub crop: Option<CropRect>,
    pub rotation: Option<u16>,
    pub flip_vertical: bool,
    pub flip_horizontal: bool,
    pub focal_point: Option<FocalPointParam>,
    pub output_format: Option<OutputFormat>,
}

#[derive(Debug, Clone, Copy)]
pub struct CropRect {
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, Copy)]
pub struct FocalPointParam {
    pub x: f64,
    pub y: f64,
    pub zoom: f64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum OutputFormat {
    Jpeg,
    Png,
    Webp,
    Gif,
}

impl OutputFormat {
    pub fn from_extension(ext: &str) -> Option<Self> {
        match ext.to_lowercase().as_str() {
            "jpg" | "jpeg" => Some(OutputFormat::Jpeg),
            "png" => Some(OutputFormat::Png),
            "webp" => Some(OutputFormat::Webp),
            "gif" => Some(OutputFormat::Gif),
            _ => None,
        }
    }

    pub fn content_type(&self) -> &'static str {
        match self {
            OutputFormat::Jpeg => "image/jpeg",
            OutputFormat::Png => "image/png",
            OutputFormat::Webp => "image/webp",
            OutputFormat::Gif => "image/gif",
        }
    }

    pub fn image_format(&self) -> ImageFormat {
        match self {
            OutputFormat::Jpeg => ImageFormat::Jpeg,
            OutputFormat::Png => ImageFormat::Png,
            OutputFormat::Webp => ImageFormat::WebP,
            OutputFormat::Gif => ImageFormat::Gif,
        }
    }
}

/// Process an image: crop, rotate, flip, resize.
pub fn process_image(
    input: &[u8],
    params: &ImageParams,
    max_width: u32,
    max_height: u32,
) -> anyhow::Result<(Vec<u8>, OutputFormat)> {
    let reader = ImageReader::new(Cursor::new(input))
        .with_guessed_format()
        .map_err(|e| anyhow::anyhow!("Cannot detect image format: {}", e))?;

    let detected_format = reader.format();
    let mut img = reader
        .decode()
        .map_err(|e| anyhow::anyhow!("Failed to decode image: {}", e))?;

    // 1. Apply rotation
    if let Some(rotation) = params.rotation {
        img = match rotation {
            90 => img.rotate90(),
            180 => img.rotate180(),
            270 => img.rotate270(),
            _ => img,
        };
    }

    // 2. Apply flips
    if params.flip_horizontal {
        img = img.fliph();
    }
    if params.flip_vertical {
        img = img.flipv();
    }

    // 3. Apply crop
    if let Some(crop) = &params.crop {
        let (iw, ih) = img.dimensions();
        let x = crop.x.min(iw.saturating_sub(1));
        let y = crop.y.min(ih.saturating_sub(1));
        let w = crop.width.min(iw - x);
        let h = crop.height.min(ih - y);
        if w > 0 && h > 0 {
            img = img.crop_imm(x, y, w, h);
        }
    }

    // 4. Apply focal point crop (if no explicit crop and dimensions given)
    if params.crop.is_none() {
        if let Some(fp) = &params.focal_point {
            if let (Some(tw), Some(th)) = (params.width, params.height) {
                if params.mode == ResizeMode::Fill {
                    img = apply_focal_crop(&img, fp, tw, th);
                }
            }
        }
    }

    // 5. Resize
    let (src_w, src_h) = img.dimensions();
    if let Some((tw, th)) = compute_target_size(src_w, src_h, params, max_width, max_height) {
        if tw != src_w || th != src_h {
            img = resize_image(&img, tw, th)?;
        }
    }

    // 6. Encode output
    let format = params
        .output_format
        .or_else(|| {
            detected_format.and_then(|f| match f {
                ImageFormat::Jpeg => Some(OutputFormat::Jpeg),
                ImageFormat::Png => Some(OutputFormat::Png),
                ImageFormat::WebP => Some(OutputFormat::Webp),
                ImageFormat::Gif => Some(OutputFormat::Gif),
                _ => Some(OutputFormat::Jpeg),
            })
        })
        .unwrap_or(OutputFormat::Jpeg);

    let mut output = Cursor::new(Vec::new());
    match format {
        OutputFormat::Jpeg => {
            let quality = (params.quality * 100.0) as u8;
            let encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(&mut output, quality);
            img.write_with_encoder(encoder)?;
        }
        _ => {
            img.write_to(&mut output, format.image_format())?;
        }
    }

    Ok((output.into_inner(), format))
}

fn apply_focal_crop(img: &DynamicImage, fp: &FocalPointParam, tw: u32, th: u32) -> DynamicImage {
    let (iw, ih) = img.dimensions();
    let target_ratio = tw as f64 / th as f64;
    let img_ratio = iw as f64 / ih as f64;

    let (crop_w, crop_h) = if img_ratio > target_ratio {
        // Image is wider: crop width
        let h = ih as f64;
        let w = h * target_ratio;
        (w, h)
    } else {
        // Image is taller: crop height
        let w = iw as f64;
        let h = w / target_ratio;
        (w, h)
    };

    // Apply zoom
    let zoom = fp.zoom.max(0.1).min(10.0);
    let zoomed_w = (crop_w / zoom).min(iw as f64);
    let zoomed_h = (crop_h / zoom).min(ih as f64);

    // Center on focal point
    let fx = fp.x * iw as f64;
    let fy = fp.y * ih as f64;
    let mut x = (fx - zoomed_w / 2.0).max(0.0);
    let mut y = (fy - zoomed_h / 2.0).max(0.0);

    // Clamp to image bounds
    if x + zoomed_w > iw as f64 {
        x = (iw as f64 - zoomed_w).max(0.0);
    }
    if y + zoomed_h > ih as f64 {
        y = (ih as f64 - zoomed_h).max(0.0);
    }

    img.crop_imm(x as u32, y as u32, zoomed_w as u32, zoomed_h as u32)
}

fn compute_target_size(
    src_w: u32,
    src_h: u32,
    params: &ImageParams,
    max_w: u32,
    max_h: u32,
) -> Option<(u32, u32)> {
    let (tw, th) = match (params.width, params.height) {
        (Some(w), Some(h)) => (w, h),
        (Some(w), None) => {
            let h = (w as f64 * src_h as f64 / src_w as f64).round() as u32;
            (w, h)
        }
        (None, Some(h)) => {
            let w = (h as f64 * src_w as f64 / src_h as f64).round() as u32;
            (w, h)
        }
        (None, None) => return None,
    };

    // Clamp to max dimensions
    let tw = tw.min(max_w).max(1);
    let th = th.min(max_h).max(1);

    // Don't upscale
    if tw >= src_w && th >= src_h {
        return None;
    }

    Some((tw, th))
}

fn resize_image(img: &DynamicImage, width: u32, height: u32) -> anyhow::Result<DynamicImage> {
    let src_image = img.to_rgba8();
    let (src_w, src_h) = src_image.dimensions();

    let src_view = FirImage::from_vec_u8(
        src_w,
        src_h,
        src_image.into_raw(),
        fast_image_resize::PixelType::U8x4,
    )?;

    let mut dst_image = FirImage::new(
        width,
        height,
        fast_image_resize::PixelType::U8x4,
    );

    let mut resizer = Resizer::new();
    resizer.resize(
        &src_view,
        &mut dst_image,
        None,
    )?;

    let buffer = dst_image.into_vec();
    let rgba = image::RgbaImage::from_raw(width, height, buffer)
        .ok_or_else(|| anyhow::anyhow!("Failed to create output image"))?;

    Ok(DynamicImage::ImageRgba8(rgba))
}

/// LRU cache for processed images.
pub struct ImageCache {
    cache: Mutex<LruCache<String, CachedImage>>,
    max_size_bytes: u64,
    current_size: Mutex<u64>,
}

struct CachedImage {
    data: Vec<u8>,
    content_type: String,
}

impl ImageCache {
    pub fn new(max_entries: usize, max_size_bytes: u64) -> Self {
        ImageCache {
            cache: Mutex::new(LruCache::new(NonZeroUsize::new(max_entries).unwrap())),
            max_size_bytes,
            current_size: Mutex::new(0),
        }
    }

    pub fn get(&self, key: &str) -> Option<(Vec<u8>, String)> {
        let mut cache = self.cache.lock().unwrap();
        cache.get(key).map(|entry| (entry.data.clone(), entry.content_type.clone()))
    }

    pub fn put(&self, key: String, data: Vec<u8>, content_type: String) {
        let size = data.len() as u64;
        let mut current = self.current_size.lock().unwrap();

        // Evict if over size limit
        if *current + size > self.max_size_bytes {
            let mut cache = self.cache.lock().unwrap();
            while *current + size > self.max_size_bytes {
                if let Some((_, evicted)) = cache.pop_lru() {
                    *current = current.saturating_sub(evicted.data.len() as u64);
                } else {
                    break;
                }
            }
        }

        let mut cache = self.cache.lock().unwrap();
        if let Some(old) = cache.push(key, CachedImage { data, content_type }) {
            *current = current.saturating_sub(old.1.data.len() as u64);
        }
        *current += size;
    }
}
