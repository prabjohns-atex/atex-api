#!/usr/bin/env python3
"""
Image service performance comparison: desk-image (Rust) vs legacy OneCMS image service.

Uploads reference images, then benchmarks various image transformations
(resize, crop, format conversion) on both servers.

Usage:
    python scripts/image-perf-test.py                          # Default servers
    python scripts/image-perf-test.py --desk http://localhost:8081 --ref http://localhost:48084/onecms
    python scripts/image-perf-test.py -n 5                     # 5 iterations per test
    python scripts/image-perf-test.py --report                 # Write CSV report

Reference images in scripts/test-images/:
    small-120kb.jpg  — 120KB JPEG (typical article thumbnail)
    large-28mb.jpg   — 28MB JPEG (full-resolution press photo)
"""

import argparse
import csv
import os
import statistics
import sys
import time

import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DESK_API = "http://localhost:8081"
REFERENCE = "http://localhost:48084/onecms"

USERNAME = "sysadmin"
PASSWORD = "sysadmin"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
IMAGE_DIR = os.path.join(SCRIPT_DIR, "test-images")

IMAGES = {
    "small-120kb": os.path.join(IMAGE_DIR, "small-120kb.jpg"),
    "large-28mb": os.path.join(IMAGE_DIR, "large-28mb.jpg"),
}

# Image transformation test cases: (label, query_params)
# These get appended to the image URL as query string or path matrix params
TRANSFORMATIONS = [
    ("original", ""),
    ("resize-w200", "w=200"),
    ("resize-w800", "w=800"),
    ("resize-w1200", "w=1200"),
    ("resize-w200-h200", "w=200&h=200"),
    ("resize-w800-h600", "w=800&h=600"),
    ("crop-200x200", "w=200&h=200&c=1"),
    ("crop-800x600", "w=800&h=600&c=1"),
]


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class C:
    PASS  = "\033[92m"
    FAIL  = "\033[91m"
    WARN  = "\033[93m"
    INFO  = "\033[94m"
    DIM   = "\033[90m"
    BOLD  = "\033[1m"
    RESET = "\033[0m"


def login(base_url: str) -> str | None:
    try:
        r = requests.post(f"{base_url}/security/token",
                          json={"username": USERNAME, "password": PASSWORD},
                          timeout=10)
        if r.status_code == 200:
            return r.json().get("token")
    except Exception:
        pass
    return None


def upload_image(base_url: str, token: str, image_path: str, label: str) -> dict | None:
    """Upload an image file and create content, returning {contentId, fileUri}."""
    filename = os.path.basename(image_path)

    # Upload file to tmp
    with open(image_path, "rb") as f:
        data = f.read()

    r = requests.post(
        f"{base_url}/file/tmp/perf-test/{label}-{filename}",
        headers={"X-Auth-Token": token, "Content-Type": "image/jpeg"},
        data=data,
        timeout=60,
    )
    if r.status_code not in (200, 201):
        print(f"  {C.FAIL}Upload failed: {r.status_code}{C.RESET}")
        return None

    file_info = r.json()
    file_uri = file_info.get("URI") or file_info.get("uri")

    # Create image content
    body = {
        "aspects": {
            "contentData": {
                "data": {
                    "_type": "atex.onecms.image",
                    "title": f"perf-test-{label}",
                    "description": f"Performance test image: {label}",
                }
            },
            "atex.Files": {
                "data": {
                    "files": {
                        filename: {
                            "filePath": filename,
                            "fileUri": file_uri,
                        }
                    }
                }
            }
        }
    }

    r = requests.post(
        f"{base_url}/content",
        headers={"X-Auth-Token": token, "Content-Type": "application/json"},
        json=body,
        timeout=30,
    )
    if r.status_code not in (200, 201):
        print(f"  {C.WARN}Content creation returned {r.status_code} — using file URI directly{C.RESET}")
        return {"contentId": None, "fileUri": file_uri, "filename": filename}

    content = r.json()
    content_id = content.get("id")
    versioned_id = content.get("version")

    return {
        "contentId": content_id,
        "versionedId": versioned_id,
        "fileUri": file_uri,
        "filename": filename,
    }


def fetch_image(url: str, token: str, timeout: int = 30) -> tuple[int, int, float]:
    """Fetch an image URL. Returns (status, body_size_bytes, elapsed_ms)."""
    t0 = time.perf_counter()
    try:
        r = requests.get(url, headers={"X-Auth-Token": token},
                         timeout=timeout, stream=True)
        data = r.content
        elapsed = (time.perf_counter() - t0) * 1000
        return r.status_code, len(data), elapsed
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000
        return 0, 0, elapsed


def build_image_url(base_url: str, content_id: str, filename: str, params: str) -> str:
    """Build image service URL for a content/filename with optional transform params."""
    # desk-api: /image/{contentId}/{filename}?w=200&h=200
    # reference: /onecms-image/img/{contentId}/{filename}?w=200&h=200
    if "/onecms" in base_url:
        # Reference server — image service is a separate webapp
        img_base = base_url.replace("/onecms", "/onecms-image")
        url = f"{img_base}/img/{content_id}/{filename}"
    else:
        url = f"{base_url}/image/{content_id}/{filename}"

    if params:
        url += "?" + params
    return url


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def run_perf_tests(desk_url: str, ref_url: str, iterations: int,
                   report_file: str | None = None):
    print(f"{C.BOLD}Image Performance Comparison{C.RESET}")
    print(f"  desk-api:  {desk_url}")
    print(f"  reference: {ref_url}")
    print(f"  iterations: {iterations}")
    print()

    # Login
    desk_token = login(desk_url)
    ref_token = login(ref_url)

    if not desk_token:
        print(f"{C.FAIL}Cannot login to desk-api{C.RESET}")
        return
    if not ref_token:
        print(f"{C.WARN}Cannot login to reference — running desk-api only{C.RESET}")

    # Upload images to both servers
    results = []  # [(image_label, transform_label, server, status, size_bytes, avg_ms, min_ms, max_ms)]

    for img_label, img_path in IMAGES.items():
        if not os.path.exists(img_path):
            print(f"{C.WARN}Image not found: {img_path}{C.RESET}")
            continue

        file_size = os.path.getsize(img_path)
        print(f"{C.INFO}Image: {img_label} ({file_size / 1024 / 1024:.1f} MB){C.RESET}")

        # Upload to desk-api
        print(f"  Uploading to desk-api...", end=" ", flush=True)
        desk_info = upload_image(desk_url, desk_token, img_path, img_label)
        if desk_info:
            print(f"{C.PASS}OK{C.RESET} (contentId={desk_info.get('contentId', 'N/A')})")
        else:
            print(f"{C.FAIL}FAILED{C.RESET}")
            continue

        # Upload to reference
        ref_info = None
        if ref_token:
            print(f"  Uploading to reference...", end=" ", flush=True)
            ref_info = upload_image(ref_url, ref_token, img_path, img_label)
            if ref_info:
                print(f"{C.PASS}OK{C.RESET} (contentId={ref_info.get('contentId', 'N/A')})")
            else:
                print(f"{C.WARN}FAILED (will skip reference){C.RESET}")

        # Run transformations
        print()
        print(f"  {'Transform':<25} {'desk-api':>30}   {'reference':>30}")
        print(f"  {'─' * 25} {'─' * 30}   {'─' * 30}")

        for tx_label, tx_params in TRANSFORMATIONS:
            desk_timings = []
            ref_timings = []

            # Desk-api
            if desk_info and desk_info.get("contentId"):
                url = build_image_url(desk_url, desk_info["contentId"],
                                       desk_info["filename"], tx_params)
                for i in range(iterations):
                    status, size, elapsed = fetch_image(url, desk_token)
                    if status == 200:
                        desk_timings.append(elapsed)

            # Reference
            if ref_info and ref_info.get("contentId") and ref_token:
                url = build_image_url(ref_url, ref_info["contentId"],
                                       ref_info["filename"], tx_params)
                for i in range(iterations):
                    status, size, elapsed = fetch_image(url, ref_token)
                    if status == 200:
                        ref_timings.append(elapsed)

            # Format results
            def fmt_timing(timings: list[float]) -> str:
                if not timings:
                    return f"{C.DIM}N/A{C.RESET}"
                avg = statistics.mean(timings)
                mn = min(timings)
                mx = max(timings)
                return f"{avg:7.0f}ms avg ({mn:.0f}-{mx:.0f}ms)"

            desk_str = fmt_timing(desk_timings)
            ref_str = fmt_timing(ref_timings)

            # Speedup indicator
            speedup = ""
            if desk_timings and ref_timings:
                d_avg = statistics.mean(desk_timings)
                r_avg = statistics.mean(ref_timings)
                if d_avg > 0:
                    ratio = r_avg / d_avg
                    if ratio > 1.1:
                        speedup = f" {C.PASS}({ratio:.1f}x faster){C.RESET}"
                    elif ratio < 0.9:
                        speedup = f" {C.FAIL}({1/ratio:.1f}x slower){C.RESET}"

            print(f"  {tx_label:<25} {desk_str:>30}   {ref_str:>30}{speedup}")

            # Collect for CSV
            if desk_timings:
                results.append((img_label, tx_label, "desk-api",
                                200, 0, statistics.mean(desk_timings),
                                min(desk_timings), max(desk_timings)))
            if ref_timings:
                results.append((img_label, tx_label, "reference",
                                200, 0, statistics.mean(ref_timings),
                                min(ref_timings), max(ref_timings)))

        print()

    # Write CSV report
    if report_file and results:
        with open(report_file, "w", newline="") as f:
            w = csv.writer(f)
            w.writerow(["image", "transform", "server", "status", "size_bytes",
                         "avg_ms", "min_ms", "max_ms"])
            for row in results:
                w.writerow(row)
        print(f"{C.INFO}Report written to {report_file}{C.RESET}")


def main():
    parser = argparse.ArgumentParser(description="Image service performance comparison")
    parser.add_argument("--desk", default=DESK_API, help=f"desk-api URL (default: {DESK_API})")
    parser.add_argument("--ref", default=REFERENCE, help=f"Reference URL (default: {REFERENCE})")
    parser.add_argument("-n", "--iterations", type=int, default=3, help="Iterations per test (default: 3)")
    parser.add_argument("--report", nargs="?", const="scripts/image-perf-report.csv",
                        help="Write CSV report (default: scripts/image-perf-report.csv)")
    args = parser.parse_args()

    run_perf_tests(args.desk, args.ref, args.iterations, args.report)


if __name__ == "__main__":
    main()
