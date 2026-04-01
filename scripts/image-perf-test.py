#!/usr/bin/env python3
"""
Image service performance & quality comparison:
  desk-image (Rust sidecar) vs legacy OneCMS image service (Java).

Uploads reference images to both servers, then benchmarks resize operations
at specific widths and compares output quality using SSIM.

Usage:
    python scripts/image-perf-test.py                                    # defaults
    python scripts/image-perf-test.py --ref http://localhost:48084/onecms
    python scripts/image-perf-test.py -n 5                               # 5 iterations
    python scripts/image-perf-test.py --report                           # CSV output
    python scripts/image-perf-test.py --save-images                      # save outputs to disk

Reference images in scripts/test-images/:
    small-120kb.jpg  — 120KB (typical article thumbnail)
    large-28mb.jpg   — 28MB (full-resolution press photo)
"""

import argparse
import csv
import io
import os
import statistics
import sys
import time
import uuid

import requests
from PIL import Image
Image.MAX_IMAGE_PIXELS = None  # disable decompression bomb check for large test images
import numpy as np
from skimage.metrics import structural_similarity as ssim

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DESK_API = "http://localhost:8081"
DESK_IMAGE = "http://localhost:8081"       # desk-api proxies /image/ to desk-image sidecar
REFERENCE = "http://localhost:48084/onecms"
REF_IMAGE = "http://localhost:48084"       # legacy image service at /image/

USERNAME = "sysadmin"
PASSWORD = "sysadmin"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_DIR = os.path.join(SCRIPT_DIR, "test-images")

IMAGES = {
    "small-120kb": os.path.join(IMAGE_DIR, "small-120kb.jpg"),
    "large-28mb": os.path.join(IMAGE_DIR, "large-28mb.jpg"),
}

WIDTHS = [100, 250, 500, 1024, 2048]

# ---------------------------------------------------------------------------
# Console
# ---------------------------------------------------------------------------

class C:
    PASS  = "\033[92m"
    FAIL  = "\033[91m"
    WARN  = "\033[93m"
    INFO  = "\033[94m"
    DIM   = "\033[90m"
    BOLD  = "\033[1m"
    RESET = "\033[0m"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def login(base_url):
    try:
        r = requests.post(f"{base_url}/security/token",
                          json={"username": USERNAME, "password": PASSWORD}, timeout=10)
        if r.status_code == 200:
            return r.json().get("token")
    except Exception:
        pass
    return None


def upload_and_create(base_url, token, image_path, label):
    """Upload file + create image content. Returns (content_id, filename) or None."""
    filename = f"perf-{label}-{uuid.uuid4().hex[:8]}.jpg"

    with open(image_path, "rb") as f:
        data = f.read()

    # Upload to tmp
    r = requests.post(f"{base_url}/file/tmp/perf-test/{filename}",
                      headers={"X-Auth-Token": token, "Content-Type": "image/jpeg",
                               "Accept": "application/json"},
                      data=data, timeout=60)
    if r.status_code not in (200, 201):
        print(f"    {C.FAIL}Upload failed: {r.status_code}{C.RESET}")
        return None

    # Handle both JSON and XML responses (reference returns XML)
    file_uri = None
    try:
        j = r.json()
        file_uri = j.get("URI") or j.get("uri")
    except Exception:
        import re
        m = re.search(r"<URI>([^<]+)</URI>", r.text)
        if m:
            file_uri = m.group(1)

    if not file_uri:
        print(f"    {C.FAIL}No file URI in upload response{C.RESET}")
        return None

    # Get dimensions from the source image
    img = Image.open(image_path)
    img_w, img_h = img.size

    # Create image content with atex.Image and atex.Files
    body = {
        "aspects": {
            "contentData": {
                "data": {
                    "_type": "atex.onecms.image",
                    "title": f"perf-test-{label}",
                    "width": img_w,
                    "height": img_h,
                    "objectType": "image",
                }
            },
            "atex.Image": {
                "data": {
                    "_type": "atex.Image",
                    "filePath": filename,
                    "width": img_w,
                    "height": img_h,
                }
            },
            "atex.Files": {
                "data": {
                    "_type": "atex.Files",
                    "files": {
                        f"{filename}_type": "com.atex.onecms.content.ContentFileInfo",
                        filename: {
                            "_type": "com.atex.onecms.content.ContentFileInfo",
                            "filePath": filename,
                            "fileUri": file_uri,
                        }
                    }
                }
            }
        }
    }
    r = requests.post(f"{base_url}/content",
                      headers={"X-Auth-Token": token, "Content-Type": "application/json"},
                      json=body, timeout=30)
    if r.status_code not in (200, 201):
        print(f"    {C.WARN}Content create: {r.status_code}{C.RESET}")
        return None

    content_id = r.json().get("id")
    return (content_id, filename)


def fetch_image(url, token, timeout=30):
    """Returns (status, image_bytes, elapsed_ms).
    Handles desk-api's redirect to desk-image sidecar by rewriting Docker
    internal hostnames to localhost equivalents."""
    t0 = time.perf_counter()
    try:
        # Don't follow redirects automatically — we may need to rewrite the Location
        r = requests.get(url, headers={"X-Auth-Token": token},
                         timeout=timeout, allow_redirects=False)
        # Follow redirects manually — rewrite Docker hostnames and handle 303
        max_redirects = 5
        while r.status_code in (301, 302, 303, 307, 308) and max_redirects > 0:
            location = r.headers.get("Location", "")
            location = location.replace("desk-image:8090", "localhost:8090")
            r = requests.get(location, headers={"X-Auth-Token": token},
                             timeout=timeout, allow_redirects=False)
            max_redirects -= 1
        elapsed = (time.perf_counter() - t0) * 1000
        if r.status_code == 200:
            return r.status_code, r.content, elapsed
        return r.status_code, None, elapsed
    except Exception as e:
        return 0, None, (time.perf_counter() - t0) * 1000


def compute_ssim(img_bytes_a, img_bytes_b):
    """Compute SSIM between two JPEG byte arrays. Returns (ssim_value, dimensions_match)."""
    try:
        a = Image.open(io.BytesIO(img_bytes_a)).convert("RGB")
        b = Image.open(io.BytesIO(img_bytes_b)).convert("RGB")

        # Resize to common dimensions for comparison if sizes differ
        if a.size != b.size:
            # Use the smaller dimensions
            w = min(a.width, b.width)
            h = min(a.height, b.height)
            a = a.resize((w, h), Image.LANCZOS)
            b = b.resize((w, h), Image.LANCZOS)
            dims_match = False
        else:
            dims_match = True

        arr_a = np.array(a)
        arr_b = np.array(b)

        # SSIM on each channel, then average
        val = ssim(arr_a, arr_b, channel_axis=2, data_range=255)
        return val, dims_match
    except Exception as e:
        return None, False


def image_dimensions(img_bytes):
    """Return (width, height) from JPEG bytes."""
    try:
        img = Image.open(io.BytesIO(img_bytes))
        return img.size
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run(desk_url, desk_image_url, ref_url, ref_image_url, iterations, report_file, save_images):
    print(f"{C.BOLD}Image Service Performance & Quality Comparison{C.RESET}")
    print(f"  desk-api:    {desk_url}")
    print(f"  desk-image:  {desk_image_url}")
    print(f"  reference:   {ref_url}")
    print(f"  ref-image:   {ref_image_url}")
    print(f"  iterations:  {iterations}")
    print(f"  widths:      {WIDTHS}")
    print()

    desk_token = login(desk_url)
    ref_token = login(ref_url)

    if not desk_token:
        print(f"{C.FAIL}Cannot login to desk-api{C.RESET}")
        return
    if not ref_token:
        print(f"{C.WARN}Cannot login to reference — desk-only mode{C.RESET}")

    results = []
    save_dir = os.path.join(SCRIPT_DIR, "test-images", "output")
    if save_images:
        os.makedirs(save_dir, exist_ok=True)

    for img_label, img_path in IMAGES.items():
        if not os.path.exists(img_path):
            print(f"{C.WARN}Skipping {img_label}: file not found{C.RESET}")
            continue

        file_size = os.path.getsize(img_path)
        orig = Image.open(img_path)
        print(f"{C.INFO}═══ {img_label} ({file_size/1024/1024:.1f} MB, {orig.width}x{orig.height}) ═══{C.RESET}")

        # Upload to both servers
        print(f"  Uploading to desk-api...", end=" ", flush=True)
        desk_info = upload_and_create(desk_url, desk_token, img_path, img_label)
        print(f"{C.PASS}OK{C.RESET}" if desk_info else f"{C.FAIL}FAILED{C.RESET}")

        ref_info = None
        if ref_token:
            print(f"  Uploading to reference...", end=" ", flush=True)
            ref_info = upload_and_create(ref_url, ref_token, img_path, img_label)
            print(f"{C.PASS}OK{C.RESET}" if ref_info else f"{C.WARN}FAILED{C.RESET}")

        if not desk_info:
            continue

        print()
        hdr = f"  {'Width':>6}  {'desk-image':>28}  {'ref-image':>28}  {'Speedup':>10}  {'SSIM':>8}  {'Dims':>16}"
        print(hdr)
        print(f"  {'─'*6}  {'─'*28}  {'─'*28}  {'─'*10}  {'─'*8}  {'─'*16}")

        for width in WIDTHS:
            desk_cid, desk_fn = desk_info
            desk_img_url = f"{desk_image_url}/image/{desk_cid}/{desk_fn}?w={width}"

            ref_img_url = None
            if ref_info:
                ref_cid, ref_fn = ref_info
                ref_img_url = f"{ref_image_url}/image/{ref_cid}/{ref_fn}?w={width}"

            # Warmup (first request may be slower due to caching)
            fetch_image(desk_img_url, desk_token, timeout=60)
            if ref_img_url:
                fetch_image(ref_img_url, ref_token, timeout=60)

            # Benchmark desk-image
            desk_timings = []
            desk_bytes = None
            for _ in range(iterations):
                status, data, elapsed = fetch_image(desk_img_url, desk_token, timeout=60)
                if status == 200 and data:
                    desk_timings.append(elapsed)
                    desk_bytes = data

            # Benchmark ref-image
            ref_timings = []
            ref_bytes = None
            if ref_img_url:
                for _ in range(iterations):
                    status, data, elapsed = fetch_image(ref_img_url, ref_token, timeout=60)
                    if status == 200 and data:
                        ref_timings.append(elapsed)
                        ref_bytes = data

            # Format timing
            def fmt(timings):
                if not timings:
                    return f"{C.DIM}{'N/A':>28}{C.RESET}"
                avg = statistics.mean(timings)
                mn = min(timings)
                return f"{avg:6.0f}ms avg ({mn:5.0f}ms min)"

            # Speedup
            speedup_str = ""
            if desk_timings and ref_timings:
                d_avg = statistics.mean(desk_timings)
                r_avg = statistics.mean(ref_timings)
                if d_avg > 0:
                    ratio = r_avg / d_avg
                    if ratio > 1.1:
                        speedup_str = f"{C.PASS}{ratio:5.1f}x{C.RESET}"
                    elif ratio < 0.9:
                        speedup_str = f"{C.FAIL}{ratio:5.1f}x{C.RESET}"
                    else:
                        speedup_str = f"{ratio:5.1f}x"

            # SSIM comparison
            ssim_str = ""
            dims_str = ""
            if desk_bytes and ref_bytes:
                ssim_val, dims_match = compute_ssim(desk_bytes, ref_bytes)
                d_dims = image_dimensions(desk_bytes)
                r_dims = image_dimensions(ref_bytes)
                dims_str = f"{d_dims[0]}x{d_dims[1]} / {r_dims[0]}x{r_dims[1]}" if d_dims and r_dims else ""

                if ssim_val is not None:
                    if ssim_val >= 0.95:
                        ssim_str = f"{C.PASS}{ssim_val:.4f}{C.RESET}"
                    elif ssim_val >= 0.85:
                        ssim_str = f"{C.WARN}{ssim_val:.4f}{C.RESET}"
                    else:
                        ssim_str = f"{C.FAIL}{ssim_val:.4f}{C.RESET}"
            elif desk_bytes:
                d_dims = image_dimensions(desk_bytes)
                dims_str = f"{d_dims[0]}x{d_dims[1]}" if d_dims else ""

            print(f"  {width:>5}px  {fmt(desk_timings)}  {fmt(ref_timings)}  {speedup_str:>10}  {ssim_str:>8}  {dims_str:>16}")

            # Save images
            if save_images and desk_bytes:
                with open(os.path.join(save_dir, f"{img_label}-w{width}-desk.jpg"), "wb") as f:
                    f.write(desk_bytes)
            if save_images and ref_bytes:
                with open(os.path.join(save_dir, f"{img_label}-w{width}-ref.jpg"), "wb") as f:
                    f.write(ref_bytes)

            # Collect for CSV
            results.append({
                "image": img_label, "width": width,
                "desk_avg_ms": f"{statistics.mean(desk_timings):.0f}" if desk_timings else "",
                "desk_min_ms": f"{min(desk_timings):.0f}" if desk_timings else "",
                "ref_avg_ms": f"{statistics.mean(ref_timings):.0f}" if ref_timings else "",
                "ref_min_ms": f"{min(ref_timings):.0f}" if ref_timings else "",
                "ssim": f"{ssim_val:.4f}" if desk_bytes and ref_bytes and ssim_val else "",
                "desk_dims": f"{d_dims[0]}x{d_dims[1]}" if desk_bytes and (d_dims := image_dimensions(desk_bytes)) else "",
                "ref_dims": f"{r_dims[0]}x{r_dims[1]}" if ref_bytes and (r_dims := image_dimensions(ref_bytes)) else "",
            })

        print()

    # CSV report
    if report_file and results:
        with open(report_file, "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=results[0].keys())
            w.writeheader()
            w.writerows(results)
        print(f"{C.INFO}Report: {report_file}{C.RESET}")


def main():
    parser = argparse.ArgumentParser(description="Image service performance & quality comparison")
    parser.add_argument("--desk", default=DESK_API)
    parser.add_argument("--desk-image", default=DESK_IMAGE)
    parser.add_argument("--ref", default=REFERENCE)
    parser.add_argument("--ref-image", default=REF_IMAGE)
    parser.add_argument("-n", "--iterations", type=int, default=3)
    parser.add_argument("--report", nargs="?", const="scripts/image-perf-report.csv")
    parser.add_argument("--save-images", action="store_true", help="Save output images to disk")
    args = parser.parse_args()

    run(args.desk, args.desk_image, args.ref, args.ref_image,
        args.iterations, args.report, args.save_images)


if __name__ == "__main__":
    main()
