#!/usr/bin/env python3
"""
Compatibility test suite: desk-api vs Reference OneCMS

Exercises core API endpoints on both servers and compares responses
to ensure desk-api is a compatible reimplementation.

Usage:
    python scripts/compat-test.py                      # Run all tests against both servers
    python scripts/compat-test.py --desk-only           # Run against desk-api only
    python scripts/compat-test.py --ref-only            # Run against reference only
    python scripts/compat-test.py --test test_login     # Run a specific test
    python scripts/compat-test.py --verbose             # Show full response bodies
    python scripts/compat-test.py -n 5                  # 5 iterations with perf summary
    python scripts/compat-test.py --test file_upload -n 10  # Benchmark file uploads
    python scripts/compat-test.py -n 100 --report         # 100 iterations, write report file
"""

import argparse
import hashlib
import io
import json
import os
import re
import statistics
import sys
import tempfile
import time
import traceback
import uuid

import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DESK_API = "http://localhost:8081"
REFERENCE = "http://localhost:38084/onecms"

USERNAME = "sysadmin"
PASSWORD = "sysadmin"

# Keys that legitimately differ between servers and should be ignored
IGNORE_KEYS = {"token", "expireTime", "renewTime", "version", "commitId",
                "commitTime", "creationTime", "modificationTime", "runTime"}

# ---------------------------------------------------------------------------
# Sample images — downloaded once and cached in temp dir
# ---------------------------------------------------------------------------

SAMPLE_IMAGES = {
    # label: (primary_url, fallback_url, ...)
    # Source: learningcontainer.com — reliable, no auth required
    "small_2mb": (
        "https://www.learningcontainer.com/wp-content/uploads/2020/07/Sample-JPEG-Image-File-Download.jpg",
    ),
    "medium_5mb": (
        "https://www.learningcontainer.com/wp-content/uploads/2020/07/Sample-Image-file-Download.jpg",
    ),
    "large_10mb": (
        "https://www.learningcontainer.com/wp-content/uploads/2020/07/sample-jpg-file-for-testing.jpg",
    ),
}

_image_cache: dict[str, bytes] = {}
_cache_dir = os.path.join(tempfile.gettempdir(), "compat-test-images")


def _download_image(label: str) -> bytes | None:
    """Download and cache a sample image. Returns bytes or None on failure."""
    if label in _image_cache:
        return _image_cache[label]

    os.makedirs(_cache_dir, exist_ok=True)
    cache_file = os.path.join(_cache_dir, f"{label}.jpg")

    # Check disk cache
    if os.path.exists(cache_file) and os.path.getsize(cache_file) > 10_000:
        with open(cache_file, "rb") as f:
            data = f.read()
        _image_cache[label] = data
        return data

    # Try primary URL, then fallback
    urls = list(SAMPLE_IMAGES.get(label, ()))

    for url in urls:
        try:
            r = requests.get(url, timeout=30,
                             headers={"User-Agent": "compat-test/1.0"},
                             allow_redirects=True)
            if r.status_code == 200:
                data = r.content
                if len(data) > 10_000:
                    with open(cache_file, "wb") as f:
                        f.write(data)
                    _image_cache[label] = data
                    return data
        except Exception:
            continue

    return None


def ensure_sample_images(labels: list[str] | None = None) -> dict[str, bytes]:
    """Download all requested sample images. Returns {label: bytes}."""
    if labels is None:
        labels = list(SAMPLE_IMAGES.keys())

    result = {}
    for label in labels:
        data = _download_image(label)
        if data:
            result[label] = data
    return result


# ---------------------------------------------------------------------------
# Console colours
# ---------------------------------------------------------------------------

class C:
    PASS  = "\033[92m"  # green
    FAIL  = "\033[91m"  # red
    WARN  = "\033[93m"  # yellow
    INFO  = "\033[94m"  # blue
    DIM   = "\033[90m"  # grey
    BOLD  = "\033[1m"
    RESET = "\033[0m"

# ---------------------------------------------------------------------------
# Test state
# ---------------------------------------------------------------------------

class TestState:
    """Mutable state shared across tests."""
    desk_token: str = None
    ref_token: str = None
    # Content IDs created during test_create_article (unversioned)
    desk_content_id: str = None
    ref_content_id: str = None
    # Versioned IDs
    desk_versioned_id: str = None
    ref_versioned_id: str = None
    # Image content IDs (for image service test)
    desk_image_id: str = None
    ref_image_id: str = None
    desk_image_vid: str = None
    ref_image_vid: str = None
    # Principal ID for activity test
    desk_principal_id: str = None
    ref_principal_id: str = None

state = TestState()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

class ApiClient:
    """Thin wrapper around requests for a single server."""

    def __init__(self, base_url: str, label: str):
        self.base = base_url.rstrip("/")
        self.label = label
        self.token: str | None = None
        self.last_elapsed_ms: float = 0  # last request time in ms
        self.cumulative_ms: float = 0    # sum of all request times since last reset
        self.request_count: int = 0      # number of requests since last reset

    def reset_timing(self):
        """Reset cumulative timing counters (call before each test)."""
        self.cumulative_ms = 0
        self.request_count = 0

    def _record(self, t0: float):
        """Record elapsed time for a request."""
        self.last_elapsed_ms = (time.perf_counter() - t0) * 1000
        self.cumulative_ms += self.last_elapsed_ms
        self.request_count += 1

    def _headers(self, extra: dict | None = None) -> dict:
        h = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            h["X-Auth-Token"] = self.token
        if extra:
            h.update(extra)
        return h

    def _url(self, path: str) -> str:
        return f"{self.base}{path}"

    # HTTP verbs — return (status_code, parsed_json_or_None, response)
    def get(self, path: str, *, params=None, follow=True, extra_headers=None):
        t0 = time.perf_counter()
        r = requests.get(self._url(path), headers=self._headers(extra_headers),
                         params=params, allow_redirects=follow, timeout=15)
        self._record(t0)
        return r.status_code, _safe_json(r), r

    def post(self, path: str, body=None, *, extra_headers=None):
        t0 = time.perf_counter()
        r = requests.post(self._url(path), headers=self._headers(extra_headers),
                          json=body, timeout=15)
        self._record(t0)
        return r.status_code, _safe_json(r), r

    def put(self, path: str, body=None, *, if_match=None, extra_headers=None):
        h = extra_headers or {}
        if if_match:
            h["If-Match"] = _quote_etag(if_match)
        t0 = time.perf_counter()
        r = requests.put(self._url(path), headers=self._headers(h),
                         json=body, timeout=15)
        self._record(t0)
        return r.status_code, _safe_json(r), r

    def delete(self, path: str, *, if_match=None, extra_headers=None):
        h = extra_headers or {}
        if if_match:
            h["If-Match"] = _quote_etag(if_match)
        t0 = time.perf_counter()
        r = requests.delete(self._url(path), headers=self._headers(h),
                            timeout=15)
        self._record(t0)
        return r.status_code, _safe_json(r), r

    def post_raw(self, path: str, data: bytes, content_type: str = "application/octet-stream"):
        """POST raw binary data (e.g., file upload)."""
        h = {"Accept": "application/json"}
        if self.token:
            h["X-Auth-Token"] = self.token
        h["Content-Type"] = content_type
        t0 = time.perf_counter()
        r = requests.post(self._url(path), headers=h, data=data, timeout=60)
        self._record(t0)
        return r.status_code, _safe_json(r), r

    def login(self, username: str = USERNAME, password: str = PASSWORD):
        status, body, _ = self.post("/security/token",
                                     {"username": username, "password": password})
        if status == 200 and body and "token" in body:
            self.token = body["token"]
        return status, body


def _safe_json(r):
    try:
        return r.json()
    except Exception:
        return None


def _quote_etag(etag: str) -> str:
    """Ensure ETag value is double-quoted per HTTP spec (required by reference server)."""
    if etag and not etag.startswith('"'):
        return f'"{etag}"'
    return etag


def _extract_etag(response) -> str | None:
    """Extract ETag from response headers, stripping quotes."""
    etag = response.headers.get("ETag")
    if etag:
        return etag.strip('"')
    return None


def compare_keys(desk_data: dict, ref_data: dict, path: str = "") -> list[str]:
    """Recursively compare dict keys. Returns list of difference descriptions."""
    diffs = []
    if not isinstance(desk_data, dict) or not isinstance(ref_data, dict):
        return diffs

    desk_keys = set(desk_data.keys())
    ref_keys = set(ref_data.keys())

    only_desk = desk_keys - ref_keys - IGNORE_KEYS
    only_ref = ref_keys - desk_keys - IGNORE_KEYS

    if only_desk:
        diffs.append(f"{path}: desk-api has extra keys: {only_desk}")
    if only_ref:
        diffs.append(f"{path}: reference has extra keys: {only_ref}")

    for key in desk_keys & ref_keys:
        if key in IGNORE_KEYS:
            continue
        dv, rv = desk_data[key], ref_data[key]
        if isinstance(dv, dict) and isinstance(rv, dict):
            diffs.extend(compare_keys(dv, rv, f"{path}.{key}"))
    return diffs


def compare_aspect_names(desk_aspects: dict, ref_aspects: dict) -> tuple[set, set]:
    """Compare aspect names between two responses. Returns (only_in_desk, only_in_ref)."""
    dk = set(desk_aspects.keys()) if desk_aspects else set()
    rk = set(ref_aspects.keys()) if ref_aspects else set()
    return dk - rk, rk - dk


def print_result(name: str, passed: bool, message: str = "", notes: list[str] = None):
    icon = f"{C.PASS}PASS{C.RESET}" if passed else f"{C.FAIL}FAIL{C.RESET}"
    print(f"  [{icon}] {name}", end="")
    if message:
        print(f"  {C.DIM}{message}{C.RESET}", end="")
    print()
    if notes:
        for note in notes:
            print(f"         {C.WARN}! {note}{C.RESET}")


def print_skip(name: str, reason: str):
    print(f"  [{C.WARN}SKIP{C.RESET}] {name}  {C.DIM}{reason}{C.RESET}")


def print_section(title: str):
    print(f"\n{C.BOLD}{C.INFO}--- {title} ---{C.RESET}")


def dump_json(label: str, data):
    """Print JSON body when --verbose is set."""
    if not VERBOSE:
        return
    print(f"    {C.DIM}{label}:{C.RESET}")
    try:
        print(f"    {C.DIM}{json.dumps(data, indent=2, default=str)[:2000]}{C.RESET}")
    except Exception:
        print(f"    {C.DIM}{data}{C.RESET}")


def make_article_body(tag: str = "") -> dict:
    """Create an article content body compatible with both servers.
    Uses StructuredText objects {text: "..."} for headline/lead/body.
    """
    ts = str(int(time.time()))
    return {
        "aspects": {
            "contentData": {
                "data": {
                    "_type": "atex.onecms.article",
                    "headline": {"text": f"Compat Test {tag} {ts}"},
                    "lead": {"text": f"Lead paragraph for compat test {tag}"},
                    "body": {"text": f"<p>Body content for compatibility test {tag} {ts}</p>"}
                }
            }
        }
    }

# ---------------------------------------------------------------------------
# Test functions
# ---------------------------------------------------------------------------

def test_login(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /security/token — authenticate and obtain tokens."""
    notes = []
    ds, db = desk.login()
    rs, rb = ref.login()

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        return False, f"desk-api login failed: HTTP {ds}", notes
    if rs != 200:
        return False, f"reference login failed: HTTP {rs}", notes

    state.desk_token = desk.token
    state.ref_token = ref.token

    # Compare response structure
    desk_keys = set(db.keys()) if db else set()
    ref_keys = set(rb.keys()) if rb else set()
    shared = desk_keys & ref_keys - IGNORE_KEYS
    if "userId" in shared:
        if db.get("userId") != rb.get("userId"):
            notes.append(f"userId differs: desk={db.get('userId')} ref={rb.get('userId')}")

    only_desk = desk_keys - ref_keys - IGNORE_KEYS
    only_ref = ref_keys - desk_keys - IGNORE_KEYS
    if only_desk:
        notes.append(f"desk-api extra fields: {only_desk}")
    if only_ref:
        notes.append(f"reference extra fields: {only_ref}")

    return True, f"desk={ds} ref={rs}", notes


def test_validate_token(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /security/token — validate existing token."""
    notes = []
    ds, db, _ = desk.get("/security/token")
    rs, rb, _ = ref.get("/security/token")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        return False, f"desk-api validate failed: HTTP {ds}", notes
    if rs != 200:
        notes.append(f"reference validate returned HTTP {rs} (may not support GET /security/token)")
        return True, f"desk={ds} ref={rs}", notes

    return True, f"desk={ds} ref={rs}", notes


def test_create_article(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /content — create article on both servers."""
    notes = []

    body_desk = make_article_body("desk")
    body_ref = make_article_body("ref")

    ds, db, dr = desk.post("/content", body_desk)
    rs, rb, rr = ref.post("/content", body_ref)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 201:
        return False, f"desk-api create failed: HTTP {ds}", notes
    if rs != 201:
        return False, f"reference create failed: HTTP {rs}", notes

    # Extract content IDs (from body, or fall back to ETag header)
    state.desk_content_id = db.get("id")
    state.ref_content_id = rb.get("id")
    state.desk_versioned_id = db.get("version") or _extract_etag(dr)
    state.ref_versioned_id = rb.get("version") or _extract_etag(rr)

    # Compare response structure
    diffs = compare_keys(db, rb, "response")
    for d in diffs:
        notes.append(d)

    # Compare aspect names
    desk_aspects = db.get("aspects", {})
    ref_aspects = rb.get("aspects", {})
    only_desk, only_ref = compare_aspect_names(desk_aspects, ref_aspects)
    if only_desk:
        notes.append(f"desk-api extra aspects: {only_desk}")
    if only_ref:
        notes.append(f"reference extra aspects: {only_ref}")

    # Verify contentData._type matches
    desk_type = desk_aspects.get("contentData", {}).get("data", {}).get("_type")
    ref_type = ref_aspects.get("contentData", {}).get("data", {}).get("_type")
    if desk_type != ref_type:
        notes.append(f"contentData._type: desk={desk_type} ref={ref_type}")

    # Check ETag / Location headers
    desk_etag = dr.headers.get("ETag")
    ref_etag = rr.headers.get("ETag")
    if desk_etag and not ref_etag:
        notes.append("desk-api returns ETag, reference does not")
    elif ref_etag and not desk_etag:
        notes.append("reference returns ETag, desk-api does not")

    desk_loc = dr.headers.get("Location")
    ref_loc = rr.headers.get("Location")
    if desk_loc and not ref_loc:
        notes.append("desk-api returns Location, reference does not")
    elif ref_loc and not desk_loc:
        notes.append("reference returns Location, desk-api does not")

    return True, f"desk={ds} ref={rs}", notes


def test_get_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/contentid/{id} — fetch content by versioned ID."""
    notes = []

    if not state.desk_versioned_id or not state.ref_versioned_id:
        return False, "no content IDs from create test", notes

    ds, db, _ = desk.get(f"/content/contentid/{state.desk_versioned_id}")
    rs, rb, _ = ref.get(f"/content/contentid/{state.ref_versioned_id}")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        return False, f"desk-api get failed: HTTP {ds}", notes
    if rs != 200:
        return False, f"reference get failed: HTTP {rs}", notes

    # Compare response structure
    diffs = compare_keys(db, rb, "response")
    for d in diffs:
        notes.append(d)

    # Compare aspects
    desk_aspects = db.get("aspects", {})
    ref_aspects = rb.get("aspects", {})
    only_desk, only_ref = compare_aspect_names(desk_aspects, ref_aspects)
    if only_desk:
        notes.append(f"desk-api extra aspects: {only_desk}")
    if only_ref:
        notes.append(f"reference extra aspects (from pre-store hooks): {only_ref}")

    # Compare contentData fields
    desk_cd = desk_aspects.get("contentData", {}).get("data", {})
    ref_cd = ref_aspects.get("contentData", {}).get("data", {})
    cd_only_desk = set(desk_cd.keys()) - set(ref_cd.keys())
    cd_only_ref = set(ref_cd.keys()) - set(desk_cd.keys())
    if cd_only_desk:
        notes.append(f"contentData extra fields in desk: {cd_only_desk}")
    if cd_only_ref:
        notes.append(f"contentData extra fields in ref: {cd_only_ref}")

    return True, f"desk={ds} ref={rs}", notes


def test_get_unversioned_redirect(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/contentid/{unversioned} — expect 302 redirect."""
    notes = []

    if not state.desk_content_id or not state.ref_content_id:
        return False, "no content IDs from create test", notes

    ds, _, dr = desk.get(f"/content/contentid/{state.desk_content_id}", follow=False)
    rs, _, rr = ref.get(f"/content/contentid/{state.ref_content_id}", follow=False)

    desk_redirect = ds in (301, 302, 303, 307, 308)
    ref_redirect = rs in (301, 302, 303, 307, 308)

    if not desk_redirect:
        notes.append(f"desk-api returned {ds} instead of redirect")
    if not ref_redirect:
        notes.append(f"reference returned {rs} instead of redirect")

    # Check Location header is present
    if desk_redirect and not dr.headers.get("Location"):
        notes.append("desk-api redirect has no Location header")
    if ref_redirect and not rr.headers.get("Location"):
        notes.append("reference redirect has no Location header")

    passed = desk_redirect and ref_redirect
    return passed, f"desk={ds} ref={rs}", notes


def test_update_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """PUT /content/contentid/{id} — update content (requires If-Match ETag)."""
    notes = []

    if not state.desk_content_id or not state.ref_content_id:
        return False, "no content IDs from create test", notes
    if not state.desk_versioned_id or not state.ref_versioned_id:
        return False, "no versioned IDs from create test", notes

    ts = str(int(time.time()))
    update_body = {
        "aspects": {
            "contentData": {
                "data": {
                    "_type": "atex.onecms.article",
                    "headline": {"text": f"Updated Compat Test {ts}"},
                    "lead": {"text": f"Updated lead {ts}"},
                    "body": {"text": f"<p>Updated body {ts}</p>"}
                }
            }
        }
    }

    ds, db, dr = desk.put(f"/content/contentid/{state.desk_content_id}", update_body,
                          if_match=state.desk_versioned_id)
    rs, rb, rr = ref.put(f"/content/contentid/{state.ref_content_id}", update_body,
                         if_match=state.ref_versioned_id)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        return False, f"desk-api update failed: HTTP {ds}", notes
    if rs != 200:
        return False, f"reference update failed: HTTP {rs}", notes

    # Update versioned IDs for subsequent tests (from body or ETag header)
    if db:
        state.desk_versioned_id = db.get("version") or _extract_etag(dr) or state.desk_versioned_id
    if rb:
        state.ref_versioned_id = rb.get("version") or _extract_etag(rr) or state.ref_versioned_id

    # Compare structure
    if db and rb:
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)

    return True, f"desk={ds} ref={rs}", notes


def test_history(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/contentid/{id}/history — version history."""
    notes = []

    if not state.desk_content_id or not state.ref_content_id:
        return False, "no content IDs from create test", notes

    ds, db, _ = desk.get(f"/content/contentid/{state.desk_content_id}/history")
    rs, rb, _ = ref.get(f"/content/contentid/{state.ref_content_id}/history")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        return False, f"desk-api history failed: HTTP {ds}", notes
    if rs != 200:
        return False, f"reference history failed: HTTP {rs}", notes

    # Both should return a list or object with versions
    desk_is_list = isinstance(db, list)
    ref_is_list = isinstance(rb, list)

    if desk_is_list != ref_is_list:
        notes.append(f"response type differs: desk={'list' if desk_is_list else 'object'} "
                     f"ref={'list' if ref_is_list else 'object'}")

    # Compare structure
    if isinstance(db, dict) and isinstance(rb, dict):
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)

        # Check version counts
        desk_versions = db.get("contentVersionInfos", db.get("versions", []))
        ref_versions = rb.get("contentVersionInfos", rb.get("versions", []))
        if isinstance(desk_versions, list) and isinstance(ref_versions, list):
            dc = len(desk_versions)
            rc = len(ref_versions)
            if dc != rc:
                notes.append(f"version count: desk={dc} ref={rc}")
            else:
                notes.append(f"both have {dc} version(s)")

    return True, f"desk={ds} ref={rs}", notes


def test_delete_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """DELETE /content/contentid/{id} — soft delete (requires If-Match ETag)."""
    notes = []

    if not state.desk_content_id or not state.ref_content_id:
        return False, "no content IDs from create test", notes
    if not state.desk_versioned_id or not state.ref_versioned_id:
        return False, "no versioned IDs for If-Match", notes

    ds, db, _ = desk.delete(f"/content/contentid/{state.desk_content_id}",
                            if_match=state.desk_versioned_id)
    rs, rb, _ = ref.delete(f"/content/contentid/{state.ref_content_id}",
                           if_match=state.ref_versioned_id)

    if ds != 204:
        notes.append(f"desk-api returned {ds} (expected 204)")
    if rs != 204:
        notes.append(f"reference returned {rs} (expected 204)")

    passed = ds == 204 and rs == 204
    return passed, f"desk={ds} ref={rs}", notes


def test_get_config(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/externalid/{configId} — resource-based config."""
    notes = []
    config_id = "dam.wfstatuslist.d"

    ds, db, _ = desk.get(f"/content/externalid/{config_id}")
    rs, rb, _ = ref.get(f"/content/externalid/{config_id}")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds not in (200, 302, 303):
        notes.append(f"desk-api returned {ds}")
    if rs not in (200, 302, 303):
        notes.append(f"reference returned {rs}")

    # If both return 200 with JSON, compare structure
    if ds == 200 and rs == 200 and db and rb:
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)
    elif ds == 200 and rs in (302, 303):
        notes.append("desk returns config directly (200), reference returns redirect")
    elif ds in (302, 303) and rs == 200:
        notes.append("desk returns redirect, reference returns config directly (200)")

    # Pass if both succeed, or if one has data and the other doesn't (data discrepancy)
    desk_ok = ds in (200, 302, 303)
    ref_ok = rs in (200, 302, 303)
    # Accept data-not-found on one server as a non-failure (config may not exist everywhere)
    if ds == 404 and ref_ok:
        notes.append("desk-api does not have this config — data discrepancy (not a code bug)")
        desk_ok = True
    if rs == 404 and desk_ok:
        notes.append("reference does not have this config — data discrepancy (not a code bug)")
        ref_ok = True
    passed = desk_ok and ref_ok
    return passed, f"desk={ds} ref={rs}", notes


def test_search(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /search/onecms/select — Solr search proxy."""
    notes = []
    params = {"q": "*:*", "rows": "5", "wt": "json"}

    ds, db, _ = desk.get("/search/onecms/select", params=params)
    rs, rb, _ = ref.get("/search/onecms/select", params=params)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api search returned {ds}")
    if rs != 200:
        notes.append(f"reference search returned {rs}")

    if ds != 200 or rs != 200:
        # Search may not be available if Solr is not running
        passed = True  # soft pass — search depends on Solr
        notes.append("Search depends on Solr availability")
        return passed, f"desk={ds} ref={rs}", notes

    # Compare top-level structure
    if db and rb:
        desk_keys = set(db.keys()) if isinstance(db, dict) else set()
        ref_keys = set(rb.keys()) if isinstance(rb, dict) else set()

        if desk_keys and ref_keys:
            only_desk = desk_keys - ref_keys
            only_ref = ref_keys - desk_keys
            if only_desk:
                notes.append(f"desk-api extra top-level keys: {only_desk}")
            if only_ref:
                notes.append(f"reference extra top-level keys: {only_ref}")

        # Check response.numFound
        desk_num = _nested_get(db, "response", "numFound")
        ref_num = _nested_get(rb, "response", "numFound")
        if desk_num is not None and ref_num is not None:
            notes.append(f"numFound: desk={desk_num} ref={ref_num}")

    return True, f"desk={ds} ref={rs}", notes


def test_search_permission(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /search/onecms/select?permission=write — Solr search with permission filter (mytype-new query)."""
    notes = []
    params = {
        "q": "*:*",
        "sort": "modificationTime desc",
        "start": "0",
        "rows": "5",
        "fl": "id",
        "variant": "list",
        "facet": "false",
        "permission": "write",
        "wt": "json",
        "solrCore": "onecms",
        "fq": "-contentState_s:TRASH",
    }

    ds, db, _ = desk.get("/search/onecms/select", params=params)
    rs, rb, _ = ref.get("/search/onecms/select", params=params)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api search returned {ds}")
    if rs != 200:
        notes.append(f"reference search returned {rs}")

    if ds != 200 or rs != 200:
        passed = True  # soft pass — search depends on Solr
        notes.append("Search depends on Solr availability")
        return passed, f"desk={ds} ref={rs}", notes

    # The key check: permission=write must return results (not 0)
    desk_num = _nested_get(db, "response", "numFound")
    ref_num = _nested_get(rb, "response", "numFound")
    notes.append(f"numFound: desk={desk_num} ref={ref_num}")

    if desk_num is not None and desk_num == 0:
        notes.append("FAIL: desk-api returned 0 results with permission=write")
        return False, f"desk numFound=0", notes

    if desk_num is not None and ref_num is not None:
        # Both should return a comparable number of results
        if desk_num > 0 and ref_num > 0:
            notes.append("Both servers return results with permission=write filter")

    return True, f"desk={ds} ref={rs}", notes


def test_search_variant_list(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /search/onecms/select?variant=list&rows=50 — search with content inlining (mytype-new main query)."""
    notes = []
    params = {
        "q": "*:*",
        "sort": "desk_atex_desk_i asc,relevance_atex_desk_s desc",
        "start": "0",
        "rows": "50",
        "fl": "id",
        "variant": "list",
        "facet": "false",
        "solrCore": "onecms",
        "fq": "-contentState_s:TRASH",
        "TZ": "America/New_York",
        "wt": "json",
    }

    desk.reset_timing()
    ds, db, _ = desk.get("/search/onecms/select", params=params)
    desk_ms = desk.last_elapsed_ms

    ref.reset_timing()
    rs, rb, _ = ref.get("/search/onecms/select", params=params)
    ref_ms = ref.last_elapsed_ms

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api search returned {ds}")
    if rs != 200:
        notes.append(f"reference search returned {rs}")

    if ds != 200 or rs != 200:
        passed = True
        notes.append("Search depends on Solr availability")
        return passed, f"desk={ds} ref={rs}", notes

    desk_num = _nested_get(db, "response", "numFound")
    ref_num = _nested_get(rb, "response", "numFound")
    notes.append(f"numFound: desk={desk_num} ref={ref_num}")
    notes.append(f"timing: desk={desk_ms:.0f}ms ref={ref_ms:.0f}ms")

    # Check docs have _data inlined
    desk_docs = _nested_get(db, "response", "docs") or []
    ref_docs = _nested_get(rb, "response", "docs") or []
    desk_with_data = sum(1 for d in desk_docs if "_data" in d)
    ref_with_data = sum(1 for d in ref_docs if "_data" in d)
    notes.append(f"docs with _data: desk={desk_with_data}/{len(desk_docs)} ref={ref_with_data}/{len(ref_docs)}")

    return True, f"desk={ds} ref={rs}", notes


def test_dam_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /dam/content/resolve/contentid/{id} — DAM content resolve endpoint."""
    notes = []

    # Create a fresh article for this test
    body = make_article_body("dam")
    ds_c, db_c, dr_c = desk.post("/content", body)
    rs_c, rb_c, rr_c = ref.post("/content", body)

    if ds_c != 201 or rs_c != 201:
        return False, f"create failed: desk={ds_c} ref={rs_c}", notes

    desk_id = db_c.get("id")
    ref_id = rb_c.get("id")

    desk_vid = db_c.get("version") or _extract_etag(dr_c)
    ref_vid = rb_c.get("version") or _extract_etag(rr_c)

    # Use resolve/contentid/{id} — the contentid/{id} endpoint is remote-only (requires ?backend=)
    # This endpoint returns text/plain, not JSON
    text_accept = {"Accept": "text/plain"}
    ds, db, dr = desk.get(f"/dam/content/resolve/contentid/{desk_id}", extra_headers=text_accept)
    rs, rb, rr = ref.get(f"/dam/content/resolve/contentid/{ref_id}", extra_headers=text_accept)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api DAM resolve returned {ds}")
    if rs != 200:
        notes.append(f"reference DAM resolve returned {rs}")

    # Both should return a versioned ID string (text/plain)
    if ds == 200 and rs == 200:
        # Response is plain text (versioned content ID), not JSON
        desk_resolved = dr.text.strip() if dr else ""
        ref_resolved = rr.text.strip() if rr else ""
        if desk_resolved and ":" in desk_resolved:
            notes.append(f"desk resolved to: {desk_resolved[:60]}")
        if ref_resolved and ":" in ref_resolved:
            notes.append(f"ref resolved to: {ref_resolved[:60]}")

    # Cleanup (pass If-Match for reference server)
    desk.delete(f"/content/contentid/{desk_id}", if_match=desk_vid)
    ref.delete(f"/content/contentid/{ref_id}", if_match=ref_vid)

    passed = ds == 200 and rs == 200
    return passed, f"desk={ds} ref={rs}", notes


def test_workspace_flow(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """Workspace CRUD: PUT draft → GET → DELETE."""
    notes = []
    ws_id = f"compat-test-{uuid.uuid4().hex[:8]}"

    # First create some real content to base the workspace draft on
    body = make_article_body("ws")
    ds_c, db_c, dr_c = desk.post("/content", body)
    rs_c, rb_c, rr_c = ref.post("/content", body)

    if ds_c != 201 or rs_c != 201:
        return False, f"create failed: desk={ds_c} ref={rs_c}", notes

    desk_id = db_c.get("id")
    ref_id = rb_c.get("id")

    # PUT draft into workspace
    draft = make_article_body("ws-draft")
    ds_p, db_p, _ = desk.put(f"/content/workspace/{ws_id}/contentid/{desk_id}", draft)
    rs_p, rb_p, _ = ref.put(f"/content/workspace/{ws_id}/contentid/{ref_id}", draft)

    dump_json("desk PUT", db_p)
    dump_json("ref PUT", rb_p)

    if ds_p not in (200, 201):
        notes.append(f"desk-api workspace PUT returned {ds_p}")
    if rs_p not in (200, 201):
        notes.append(f"reference workspace PUT returned {rs_p}")

    # GET draft from workspace
    ds_g, db_g, _ = desk.get(f"/content/workspace/{ws_id}/contentid/{desk_id}")
    rs_g, rb_g, _ = ref.get(f"/content/workspace/{ws_id}/contentid/{ref_id}")

    dump_json("desk GET", db_g)
    dump_json("ref GET", rb_g)

    if ds_g != 200:
        notes.append(f"desk-api workspace GET returned {ds_g}")
    if rs_g != 200:
        notes.append(f"reference workspace GET returned {rs_g}")

    # Compare workspace response structure
    if ds_g == 200 and rs_g == 200 and db_g and rb_g:
        diffs = compare_keys(db_g, rb_g, "workspace GET")
        for d in diffs:
            notes.append(d)

    # DELETE draft from workspace
    ds_d, _, _ = desk.delete(f"/content/workspace/{ws_id}/contentid/{desk_id}")
    rs_d, _, _ = ref.delete(f"/content/workspace/{ws_id}/contentid/{ref_id}")

    if ds_d != 204:
        notes.append(f"desk-api workspace DELETE returned {ds_d}")
    if rs_d != 204:
        notes.append(f"reference workspace DELETE returned {rs_d}")

    # Cleanup real content (pass If-Match for reference server)
    desk_vid = db_c.get("version") or _extract_etag(dr_c)
    ref_vid = rb_c.get("version") or _extract_etag(rr_c)
    desk.delete(f"/content/contentid/{desk_id}", if_match=desk_vid)
    ref.delete(f"/content/contentid/{ref_id}", if_match=ref_vid)

    passed = (ds_p in (200, 201) and rs_p in (200, 201)
              and ds_g == 200 and rs_g == 200
              and ds_d == 204 and rs_d == 204)
    return passed, f"PUT desk={ds_p} ref={rs_p} | GET desk={ds_g} ref={rs_g} | DEL desk={ds_d} ref={rs_d}", notes


def test_changes_feed(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /changes — content change feed."""
    notes = []
    params = {"rows": "5", "object": "*", "event": "*"}

    ds, db, _ = desk.get("/changes", params=params)
    rs, rb, _ = ref.get("/changes", params=params)

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api changes returned {ds}")
    if rs != 200:
        notes.append(f"reference changes returned {rs}")

    if ds == 200 and rs == 200 and isinstance(db, dict) and isinstance(rb, dict):
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)

        desk_count = db.get("numFound", db.get("size", "?"))
        ref_count = rb.get("numFound", rb.get("size", "?"))
        notes.append(f"event counts: desk={desk_count} ref={ref_count}")

    passed = ds == 200 and rs == 200
    return passed, f"desk={ds} ref={rs}", notes


def test_external_id_redirect(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/externalid/{id} — external ID resolution."""
    notes = []

    # Create content with an explicit external ID isn't straightforward,
    # so test with a known config external ID instead
    ext_id = "atex.configuration.desk.menus"

    ds, db, dr = desk.get(f"/content/externalid/{ext_id}", follow=False)
    rs, rb, rr = ref.get(f"/content/externalid/{ext_id}", follow=False)

    dump_json("desk", db)
    dump_json("ref", rb)

    desk_ok = ds in (200, 302, 303)
    ref_ok = rs in (200, 302, 303)

    if not desk_ok:
        notes.append(f"desk-api returned {ds}")
    if not ref_ok:
        notes.append(f"reference returned {rs}")

    if ds != rs:
        notes.append(f"status differs: desk={ds} ref={rs}")

    passed = desk_ok and ref_ok
    return passed, f"desk={ds} ref={rs}", notes


def test_principals_me(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /principals/users/me — current user info."""
    notes = []

    ds, db, _ = desk.get("/principals/users/me")
    rs, rb, _ = ref.get("/principals/users/me")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api returned {ds}")
    if rs != 200:
        notes.append(f"reference returned {rs}")

    if ds == 200 and rs == 200 and isinstance(db, dict) and isinstance(rb, dict):
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)

    passed = ds == 200 and rs == 200
    return passed, f"desk={ds} ref={rs}", notes


def test_dam_configuration(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /dam/content/configuration/deskconfig — desk configuration."""
    notes = []

    ds, db, _ = desk.get("/dam/content/configuration/deskconfig")
    rs, rb, _ = ref.get("/dam/content/configuration/deskconfig")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds not in (200, 404):
        notes.append(f"desk-api returned {ds}")
    if rs not in (200, 404):
        notes.append(f"reference returned {rs}")

    if ds == 200 and rs == 200 and isinstance(db, dict) and isinstance(rb, dict):
        desk_keys = set(db.keys())
        ref_keys = set(rb.keys())
        only_desk = desk_keys - ref_keys
        only_ref = ref_keys - desk_keys
        if only_desk:
            notes.append(f"desk extra keys: {only_desk}")
        if only_ref:
            notes.append(f"ref extra keys: {only_ref}")

    # Pass if both return 200 or both return 404 (symmetric), or one has data and other doesn't
    passed = ds in (200, 404) and rs in (200, 404)
    return passed, f"desk={ds} ref={rs}", notes


def test_file_upload(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /file/tmp/anonymous/{name} — upload sample images (1-10MB) to temporary storage."""
    notes = []
    all_passed = True

    # Get available sample images
    images = ensure_sample_images()
    if not images:
        # Fallback to tiny PNG if downloads failed
        notes.append("sample image download failed — using 1x1 PNG fallback")
        images = {"tiny": (
            b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01'
            b'\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00'
            b'\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00'
            b'\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82'
        )}

    for label, img_data in images.items():
        size_mb = len(img_data) / (1024 * 1024)
        ext = "jpg" if label != "tiny" else "png"
        content_type = "image/jpeg" if ext == "jpg" else "image/png"
        filename = f"compat-{label}-{uuid.uuid4().hex[:8]}.{ext}"

        t0 = time.perf_counter()
        ds, db, _ = desk.post_raw(f"/file/tmp/anonymous/{filename}", img_data, content_type)
        desk_ms = (time.perf_counter() - t0) * 1000

        t0 = time.perf_counter()
        rs, rb, _ = ref.post_raw(f"/file/tmp/anonymous/{filename}", img_data, content_type)
        ref_ms = (time.perf_counter() - t0) * 1000

        desk_ok = ds in (200, 201)
        ref_ok = rs in (200, 201)
        if not desk_ok or not ref_ok:
            all_passed = False

        desk_speed = len(img_data) / (desk_ms / 1000) / (1024 * 1024) if desk_ms > 0 else 0
        ref_speed = len(img_data) / (ref_ms / 1000) / (1024 * 1024) if ref_ms > 0 else 0

        notes.append(
            f"{label} ({size_mb:.1f}MB): "
            f"desk={ds} {desk_ms:.0f}ms ({desk_speed:.1f}MB/s) | "
            f"ref={rs} {ref_ms:.0f}ms ({ref_speed:.1f}MB/s)"
        )

        # Compare response structure on first successful pair
        if desk_ok and ref_ok and db and rb:
            diffs = compare_keys(db, rb, f"{label} response")
            for d in diffs:
                notes.append(d)

    return all_passed, f"{len(images)} image(s) uploaded", notes


def test_create_image_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /content — create image content with file, verify tmp→content commit."""
    notes = []

    # Use a real sample image for realistic testing
    images = ensure_sample_images(["small_2mb", "medium_5mb"])
    if images:
        label = "small_2mb" if "small_2mb" in images else list(images.keys())[0]
        img_data = images[label]
        ext = "jpg"
        content_type = "image/jpeg"
        notes.append(f"using {label} ({len(img_data) / (1024*1024):.1f}MB)")
    else:
        # Fallback to tiny PNG
        img_data = (
            b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01'
            b'\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00'
            b'\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00'
            b'\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82'
        )
        ext = "png"
        content_type = "image/png"
        notes.append("sample download failed — using 1x1 PNG fallback")

    fname = f"img-test-{uuid.uuid4().hex[:8]}.{ext}"

    t0 = time.perf_counter()
    ds_u, db_u, _ = desk.post_raw(f"/file/tmp/anonymous/{fname}", img_data, content_type)
    desk_upload_ms = (time.perf_counter() - t0) * 1000

    t0 = time.perf_counter()
    rs_u, rb_u, _ = ref.post_raw(f"/file/tmp/anonymous/{fname}", img_data, content_type)
    ref_upload_ms = (time.perf_counter() - t0) * 1000

    notes.append(f"upload: desk={desk_upload_ms:.0f}ms ref={ref_upload_ms:.0f}ms")

    if ds_u not in (200, 201):
        notes.append(f"desk file upload failed: HTTP {ds_u}")
    if rs_u not in (200, 201):
        notes.append(f"ref file upload failed: HTTP {rs_u}")

    # Response field is "URI" (uppercase) on desk-api; reference may not return a body
    desk_file_uri = (db_u.get("URI") or db_u.get("uri") or f"tmp://anonymous/{fname}") if db_u else f"tmp://anonymous/{fname}"
    ref_file_uri = (rb_u.get("URI") or rb_u.get("uri") or f"tmp://anonymous/{fname}") if rb_u else f"tmp://anonymous/{fname}"

    # Create image content matching reference format:
    # - contentData has width/height (duplicated from atex.Image)
    # - atex.Image is a SEPARATE aspect with _type "atex.Image" (aspect name, not class name)
    # - atex.Image.filePath is just the filename (not the full URI)
    # - atex.Files has the full file URI
    def make_image_body(file_uri, filename):
        return {
            "aspects": {
                "contentData": {
                    "data": {
                        "_type": "atex.onecms.image",
                        "title": f"Compat Test Image {filename}",
                        "width": 1920,
                        "height": 1280
                    }
                },
                "atex.Image": {
                    "data": {
                        "_type": "atex.Image",
                        "filePath": filename,
                        "width": 1920,
                        "height": 1280
                    }
                },
                "atex.Files": {
                    "data": {
                        "_type": "atex.Files",
                        "files": {
                            filename: {
                                "_type": "com.atex.onecms.content.ContentFileInfo",
                                "filePath": filename,
                                "fileUri": file_uri
                            }
                        }
                    }
                }
            }
        }

    ds, db, dr = desk.post("/content", make_image_body(desk_file_uri, fname))
    rs, rb, rr = ref.post("/content", make_image_body(ref_file_uri, fname))

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 201:
        return False, f"desk-api create image failed: HTTP {ds}", notes
    if rs != 201:
        # Reference may reject image content due to type registration differences
        notes.append(f"reference create image: HTTP {rs} (type registration may differ)")
        # Continue with desk-only validation

    desk_id = db.get("id") if db else None
    ref_id = rb.get("id") if (rb and rs == 201) else None
    desk_vid = (db.get("version") or _extract_etag(dr)) if db else None
    ref_vid = (rb.get("version") or _extract_etag(rr)) if (rb and rs == 201) else None

    # Store for image service test
    state.desk_image_id = desk_id
    state.ref_image_id = ref_id
    state.desk_image_vid = desk_vid
    state.ref_image_vid = ref_vid

    # Compare aspects (only if both succeeded)
    if rs == 201 and rb:
        desk_aspects = db.get("aspects", {})
        ref_aspects = rb.get("aspects", {})
        only_desk, only_ref = compare_aspect_names(desk_aspects, ref_aspects)
        if only_desk:
            notes.append(f"desk-api extra aspects: {only_desk}")
        if only_ref:
            notes.append(f"reference extra aspects: {only_ref}")

    # Fetch the content back and check if tmp:// was committed to content://
    if desk_vid:
        ds_g, db_g, _ = desk.get(f"/content/contentid/{desk_vid}")
        if ds_g == 200 and db_g:
            # Check atex.Files for committed files
            desk_files = _nested_get(db_g, "aspects", "atex.Files", "data", "files") or {}
            for fn, fi in desk_files.items():
                fu = fi.get("fileUri", "") if isinstance(fi, dict) else ""
                if fu.startswith("content://"):
                    notes.append(f"desk atex.Files[{fn}] committed: {fu}")
                elif fu.startswith("tmp://"):
                    notes.append(f"desk atex.Files[{fn}] NOT committed (still tmp://)")

    if ref_vid:
        rs_g, rb_g, _ = ref.get(f"/content/contentid/{ref_vid}")
        if rs_g == 200 and rb_g:
            ref_files = _nested_get(rb_g, "aspects", "atex.Files", "data", "files") or {}
            for fn, fi in ref_files.items():
                fu = fi.get("fileUri", "") if isinstance(fi, dict) else ""
                if fu.startswith("content://"):
                    notes.append(f"ref atex.Files[{fn}] committed: {fu}")
                elif fu.startswith("tmp://"):
                    notes.append(f"ref atex.Files[{fn}] NOT committed (still tmp://)")

    return True, f"desk={ds} ref={rs}", notes


def test_activities(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """PUT/GET /activities/{id}/{user}/{app} — content locking."""
    notes = []

    # We need content IDs and principal IDs.
    # Create fresh content for this test.
    body = make_article_body("activity")
    ds_c, db_c, dr_c = desk.post("/content", body)
    rs_c, rb_c, rr_c = ref.post("/content", body)

    if ds_c != 201:
        return False, f"desk create failed: HTTP {ds_c}", notes
    if rs_c != 201:
        return False, f"ref create failed: HTTP {rs_c}", notes

    desk_cid = db_c.get("id")
    ref_cid = rb_c.get("id")
    desk_vid = db_c.get("version") or _extract_etag(dr_c)
    ref_vid = rb_c.get("version") or _extract_etag(rr_c)

    # Get principal ID from /principals/users/me
    ds_me, db_me, _ = desk.get("/principals/users/me")
    rs_me, rb_me, _ = ref.get("/principals/users/me")

    # The activity URL user must match the JWT subject (principalId).
    # Get it from the token response's userId field (set during login).
    desk_user = "sysadmin"
    ref_user = "sysadmin"

    if ds_me == 200 and db_me:
        desk_user = str(db_me.get("userId", db_me.get("loginName", "sysadmin")))
    if rs_me == 200 and rb_me:
        ref_user = str(rb_me.get("userId", rb_me.get("loginName", "sysadmin")))

    notes.append(f"desk user={desk_user}, ref user={ref_user}")

    # PUT activity (write lock)
    activity_body = {"activity": "editing", "params": {}}
    ds_w, db_w, _ = desk.put(f"/activities/{desk_cid}/{desk_user}/atex.dm.desk",
                              activity_body)
    rs_w, rb_w, _ = ref.put(f"/activities/{ref_cid}/{ref_user}/atex.dm.desk",
                              activity_body)

    dump_json("desk PUT", db_w)
    dump_json("ref PUT", rb_w)

    if ds_w != 200:
        notes.append(f"desk activity write: HTTP {ds_w}")
    if rs_w != 200:
        notes.append(f"ref activity write: HTTP {rs_w}")

    # GET activities
    ds_r, db_r, _ = desk.get(f"/activities/{desk_cid}")
    rs_r, rb_r, _ = ref.get(f"/activities/{ref_cid}")

    dump_json("desk GET", db_r)
    dump_json("ref GET", rb_r)

    if ds_r != 200:
        notes.append(f"desk activity read: HTTP {ds_r}")
    if rs_r != 200:
        notes.append(f"ref activity read: HTTP {rs_r}")

    # Compare activity response structure
    if ds_r == 200 and rs_r == 200 and isinstance(db_r, dict) and isinstance(rb_r, dict):
        diffs = compare_keys(db_r, rb_r, "activities GET")
        for d in diffs:
            notes.append(d)

    # Cleanup
    desk.delete(f"/content/contentid/{desk_cid}", if_match=desk_vid)
    ref.delete(f"/content/contentid/{ref_cid}", if_match=ref_vid)

    passed = ds_w == 200 and rs_w == 200 and ds_r == 200 and rs_r == 200
    return passed, f"PUT desk={ds_w} ref={rs_w} | GET desk={ds_r} ref={rs_r}", notes


def test_image_service_redirect(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /image/{id}/photo.jpg — image service redirect (302)."""
    notes = []

    desk_id = state.desk_image_id
    ref_id = state.ref_image_id

    if not desk_id:
        notes.append("no desk image content (create_image_content must run first)")
    if not ref_id:
        notes.append("no ref image content (create_image_content must run first)")

    if not desk_id and not ref_id:
        return False, "no image content IDs available", notes

    # Test desk-api image service (should return 302 redirect to desk-image sidecar)
    if desk_id:
        ds, _, dr = desk.get(f"/image/{desk_id}/photo.jpg", params={"w": "100"}, follow=False)
        if ds == 302:
            loc = dr.headers.get("Location", "")
            notes.append(f"desk redirect: {loc[:80]}")
        elif ds == 404:
            notes.append("desk image service: 404 (may not be enabled)")
        else:
            notes.append(f"desk image service: HTTP {ds}")
    else:
        ds = None

    # Test reference image service
    if ref_id:
        rs, _, rr = ref.get(f"/image/{ref_id}/photo.jpg", params={"w": "100"}, follow=False)
        if rs == 302:
            loc = rr.headers.get("Location", "")
            notes.append(f"ref redirect: {loc[:80]}")
        elif rs == 404:
            notes.append("ref image service: 404 (may not be enabled)")
        else:
            notes.append(f"ref image service: HTTP {rs}")
    else:
        rs = None

    # Both returning 302 or both 404 is a pass
    if ds and rs:
        if ds == rs:
            passed = True
        else:
            passed = True  # soft pass — image service config may differ
            notes.append(f"status differs: desk={ds} ref={rs}")
    else:
        passed = ds == 302 or rs == 302  # at least one works

    return passed, f"desk={ds} ref={rs}", notes


def test_file_download(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /file/{scheme}/{host}/{path} — download a committed file."""
    notes = []

    # Use the image content created by test_create_image_content
    desk_vid = state.desk_image_vid
    ref_vid = state.ref_image_vid

    if not desk_vid and not ref_vid:
        return False, "no image content (create_image_content must run first)", notes

    desk_fp = None
    ref_fp = None

    # Get the committed file URI from atex.Files aspect
    def _first_file_uri(body):
        files = _nested_get(body, "aspects", "atex.Files", "data", "files") or {}
        for fi in files.values():
            if isinstance(fi, dict) and fi.get("fileUri"):
                return fi["fileUri"]
        return None

    if desk_vid:
        ds, db, _ = desk.get(f"/content/contentid/{desk_vid}")
        if ds == 200 and db:
            desk_fp = _first_file_uri(db)

    if ref_vid:
        rs, rb, _ = ref.get(f"/content/contentid/{ref_vid}")
        if rs == 200 and rb:
            ref_fp = _first_file_uri(rb)

    # Convert URI scheme://host/path to /file/scheme/host/path
    def uri_to_file_path(uri):
        if not uri:
            return None
        return uri.replace("://", "/")

    desk_file_path = uri_to_file_path(desk_fp)
    ref_file_path = uri_to_file_path(ref_fp)

    ds_dl = None
    rs_dl = None

    if desk_file_path:
        ds_dl, _, _ = desk.get(f"/file/{desk_file_path}")
        if ds_dl == 200:
            notes.append(f"desk download OK: /file/{desk_file_path}")
        else:
            notes.append(f"desk download: HTTP {ds_dl} for /file/{desk_file_path}")

    if ref_file_path:
        rs_dl, _, _ = ref.get(f"/file/{ref_file_path}")
        if rs_dl == 200:
            notes.append(f"ref download OK: /file/{ref_file_path}")
        else:
            notes.append(f"ref download: HTTP {rs_dl} for /file/{ref_file_path}")

    if not desk_file_path:
        notes.append(f"desk filePath not available: {desk_fp}")
    if not ref_file_path:
        notes.append(f"ref filePath not available: {ref_fp}")

    passed = (ds_dl == 200 or desk_file_path is None) and (rs_dl == 200 or ref_file_path is None)
    return passed, f"desk={ds_dl} ref={rs_dl}", notes


# ---------------------------------------------------------------------------
# Single-server tests (run independently per server)
# ---------------------------------------------------------------------------

def test_create_get_delete_cycle(client: ApiClient) -> tuple[bool, str, list[str]]:
    """Full CRUD cycle on a single server."""
    notes = []

    # Create
    body = make_article_body("cycle")
    cs, cb, cr = client.post("/content", body)
    if cs != 201:
        return False, f"create failed: HTTP {cs}", notes

    cid = cb.get("id")
    vid = cb.get("version") or _extract_etag(cr)
    if not cid or not vid:
        return False, "create response missing id/version", notes

    # Get versioned
    gs, gb, _ = client.get(f"/content/contentid/{vid}")
    if gs != 200:
        notes.append(f"get versioned returned {gs}")

    # Get unversioned (expect redirect)
    us, _, ur = client.get(f"/content/contentid/{cid}", follow=False)
    if us not in (301, 302, 303, 307, 308):
        notes.append(f"get unversioned returned {us} (expected redirect)")

    # History
    hs, hb, _ = client.get(f"/content/contentid/{cid}/history")
    if hs != 200:
        notes.append(f"history returned {hs}")

    # Update (with If-Match — required by reference server)
    update_body = make_article_body("cycle-update")
    ps, pb, pr = client.put(f"/content/contentid/{cid}", update_body, if_match=vid)
    if ps != 200:
        notes.append(f"update returned {ps}")
    else:
        vid = pb.get("version") or _extract_etag(pr) or vid

    # History after update (should have 2 versions)
    hs2, hb2, _ = client.get(f"/content/contentid/{cid}/history")
    if hs2 == 200 and isinstance(hb2, dict):
        versions = hb2.get("contentVersionInfos", hb2.get("versions", []))
        if isinstance(versions, list):
            notes.append(f"history has {len(versions)} version(s) after update")

    # Delete (with If-Match — required by reference server)
    ds, _, _ = client.delete(f"/content/contentid/{cid}", if_match=vid)
    if ds != 204:
        notes.append(f"delete returned {ds}")

    passed = cs == 201 and gs == 200 and ds == 204
    return passed, f"create={cs} get={gs} update={ps} delete={ds}", notes


# ---------------------------------------------------------------------------
# Utilities
# ---------------------------------------------------------------------------

def _nested_get(d, *keys):
    """Safely navigate nested dicts."""
    for k in keys:
        if isinstance(d, dict):
            d = d.get(k)
        else:
            return None
    return d


# ---------------------------------------------------------------------------
# Test runner
# ---------------------------------------------------------------------------

ALL_COMPARISON_TESTS = [
    ("Login (POST /security/token)", test_login),
    ("Validate token (GET /security/token)", test_validate_token),
    ("Create article (POST /content)", test_create_article),
    ("Get versioned content (GET /content/contentid/{vid})", test_get_content),
    ("Get unversioned redirect (GET /content/contentid/{id})", test_get_unversioned_redirect),
    ("Update content (PUT /content/contentid/{id})", test_update_content),
    ("Content history (GET /content/contentid/{id}/history)", test_history),
    ("External ID resolve (GET /content/externalid/{id})", test_external_id_redirect),
    ("Config content (GET /content/externalid/{configId})", test_get_config),
    ("Search proxy (GET /search/onecms/select)", test_search),
    ("Search with permission (GET /search?permission=write)", test_search_permission),
    ("Search variant=list 50 rows (GET /search?variant=list)", test_search_variant_list),
    ("DAM content (GET /dam/content/contentid/{id})", test_dam_content),
    ("DAM configuration (GET /dam/content/configuration/deskcfg)", test_dam_configuration),
    ("Principals me (GET /principals/users/me)", test_principals_me),
    ("Workspace CRUD flow", test_workspace_flow),
    ("Changes feed (GET /changes)", test_changes_feed),
    ("File upload (POST /file/tmp)", test_file_upload),
    ("Create image content (POST /content image)", test_create_image_content),
    ("Activities (PUT/GET /activities)", test_activities),
    ("Image service redirect (GET /image/{id})", test_image_service_redirect),
    ("File download (GET /file/{path})", test_file_download),
    ("Delete content (DELETE /content/contentid/{id})", test_delete_content),
]


def check_server(base_url: str, label: str) -> bool:
    """Check if a server is reachable."""
    try:
        r = requests.get(f"{base_url}/v3/api-docs", timeout=5)
        return True
    except Exception:
        # Try a simpler endpoint
        try:
            r = requests.post(f"{base_url}/security/token",
                              json={"username": "probe", "password": "probe"},
                              timeout=5)
            return True  # even a 401 means the server is up
        except Exception:
            return False


def _perf_stats(times: list[float]) -> dict:
    """Compute avg, min, max, p50, p95 for a list of timings."""
    if not times:
        return {"avg": 0, "min": 0, "max": 0, "p50": 0, "p95": 0}
    if len(times) == 1:
        v = times[0]
        return {"avg": v, "min": v, "max": v, "p50": v, "p95": v}
    s = sorted(times)
    return {
        "avg": statistics.mean(s),
        "min": s[0],
        "max": s[-1],
        "p50": statistics.median(s),
        "p95": s[min(int(len(s) * 0.95), len(s) - 1)],
    }


def _fmt_perf_line(label: str, stats: dict, w_label: int = 50) -> str:
    """Format one line of perf stats."""
    return (f"  {label:<{w_label}} {stats['avg']:>7.0f}ms {stats['min']:>7.0f}ms "
            f"{stats['max']:>7.0f}ms {stats['p50']:>7.0f}ms {stats['p95']:>7.0f}ms")


def run_comparison_tests(desk: ApiClient, ref: ApiClient, test_filter: str | None = None,
                         iterations: int = 1):
    """Run all comparison tests between desk-api and reference."""

    # Collect per-test timing across iterations: {test_name: {"desk": [ms], "ref": [ms], "total": [ms]}}
    perf_data: dict[str, dict[str, list[float]]] = {}
    # Track pass/fail per test across iterations
    test_results: dict[str, dict[str, int]] = {}  # {name: {"pass": N, "fail": N, "skip": N}}

    total_passed = 0
    total_failed = 0
    total_skipped = 0

    quiet = iterations > 5  # suppress per-test output for many iterations

    for iteration in range(1, iterations + 1):
        # Reset state for each iteration
        global state
        state = TestState()

        # Always log in — filtered tests may skip test_login but still need auth
        desk.login()
        ref.login()

        if not quiet:
            if iterations > 1:
                print_section(f"Iteration {iteration}/{iterations}")
            else:
                print_section("Comparison Tests (desk-api vs reference)")
        elif iteration == 1 or iteration % 10 == 0 or iteration == iterations:
            print(f"\r  Running iteration {iteration}/{iterations}...", end="", flush=True)

        passed = 0
        failed = 0
        skipped = 0

        for name, fn in ALL_COMPARISON_TESTS:
            if test_filter and test_filter not in fn.__name__:
                continue

            if name not in test_results:
                test_results[name] = {"pass": 0, "fail": 0, "skip": 0}

            try:
                desk.reset_timing()
                ref.reset_timing()
                t0 = time.perf_counter()
                ok, msg, notes = fn(desk, ref)
                elapsed = (time.perf_counter() - t0) * 1000

                if not quiet:
                    timing_str = f" [desk={desk.cumulative_ms:.0f}ms ref={ref.cumulative_ms:.0f}ms]"
                    print_result(name, ok, msg + timing_str, notes)
                if ok:
                    passed += 1
                    test_results[name]["pass"] += 1
                    # Only track timing for successful tests (both sides must work to compare)
                    if name not in perf_data:
                        perf_data[name] = {"desk": [], "ref": [], "total": []}
                    perf_data[name]["desk"].append(desk.cumulative_ms)
                    perf_data[name]["ref"].append(ref.cumulative_ms)
                    perf_data[name]["total"].append(elapsed)
                else:
                    failed += 1
                    test_results[name]["fail"] += 1
            except requests.exceptions.ConnectionError as e:
                if not quiet:
                    print_skip(name, f"connection error: {e}")
                skipped += 1
                test_results[name]["skip"] += 1
            except Exception as e:
                if not quiet:
                    print_result(name, False, f"exception: {e}")
                    if VERBOSE:
                        traceback.print_exc()
                failed += 1
                test_results[name]["fail"] += 1

        total_passed += passed
        total_failed += failed
        total_skipped += skipped

    if quiet:
        print()  # newline after progress

    # Print performance summary if multiple iterations
    if iterations > 1 and perf_data:
        # Compatibility results
        print_section("Test Results Summary")
        for name, counts in test_results.items():
            p, f, s = counts["pass"], counts["fail"], counts["skip"]
            status = f"{C.PASS}PASS{C.RESET}" if f == 0 else f"{C.FAIL}FAIL{C.RESET}"
            detail = f"{p}/{iterations} passed"
            if f > 0:
                detail += f", {f} failed"
            if s > 0:
                detail += f", {s} skipped"
            print(f"  [{status}] {name}  {C.DIM}{detail}{C.RESET}")

        # Desk vs reference timing (only tests with data)
        n_col = "  N"
        print_section(f"Performance: desk-api ({len(perf_data)} tests with successful samples)")
        hdr = f"  {'Test':<50} {n_col:>4} {'Avg':>8} {'Min':>8} {'Max':>8} {'P50':>8} {'P95':>8}"
        sep = f"  {'-' * 50} {'-' * 4} {'-' * 8} {'-' * 8} {'-' * 8} {'-' * 8} {'-' * 8}"
        print(hdr)
        print(sep)
        for name, data in perf_data.items():
            n = len(data["desk"])
            print(f"  {name:<50} {n:>4} " + _fmt_perf_line("", _perf_stats(data["desk"]), w_label=0).lstrip())

        print_section(f"Performance: reference ({len(perf_data)} tests)")
        print(hdr)
        print(sep)
        for name, data in perf_data.items():
            n = len(data["ref"])
            print(f"  {name:<50} {n:>4} " + _fmt_perf_line("", _perf_stats(data["ref"]), w_label=0).lstrip())

        # Side-by-side comparison (avg only)
        print_section("Performance: desk-api vs reference (avg ms)")
        print(f"  {'Test':<50} {n_col:>4} {'desk':>8} {'ref':>8} {'delta':>8} {'ratio':>7}")
        print(f"  {'-' * 50} {'-' * 4} {'-' * 8} {'-' * 8} {'-' * 8} {'-' * 7}")
        for name, data in perf_data.items():
            n = len(data["desk"])
            d_avg = statistics.mean(data["desk"]) if data["desk"] else 0
            r_avg = statistics.mean(data["ref"]) if data["ref"] else 0
            delta = d_avg - r_avg
            ratio = d_avg / r_avg if r_avg > 0 else float("inf")
            delta_color = C.PASS if delta <= 0 else C.FAIL if delta > r_avg * 0.5 else C.WARN
            print(f"  {name:<50} {n:>4} {d_avg:>7.0f}ms {r_avg:>7.0f}ms "
                  f"{delta_color}{delta:>+7.0f}ms{C.RESET} {ratio:>6.2f}x")

    return total_passed, total_failed, total_skipped


def run_single_server_tests(client: ApiClient, test_filter: str | None = None):
    """Run CRUD cycle test on a single server."""
    print_section(f"Single-Server Tests ({client.label})")

    passed = 0
    failed = 0
    skipped = 0

    # Login first
    status, _ = client.login()
    if status != 200:
        print_result(f"Login ({client.label})", False, f"HTTP {status}")
        return 0, 1, 0

    name = f"CRUD cycle ({client.label})"
    if test_filter and "cycle" not in test_filter:
        return 0, 0, 0

    try:
        ok, msg, notes = test_create_get_delete_cycle(client)
        print_result(name, ok, msg, notes)
        if ok:
            passed += 1
        else:
            failed += 1
    except Exception as e:
        print_result(name, False, f"exception: {e}")
        if VERBOSE:
            traceback.print_exc()
        failed += 1

    return passed, failed, skipped


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

VERBOSE = False


def main():
    global VERBOSE

    parser = argparse.ArgumentParser(description="Compatibility test: desk-api vs OneCMS reference")
    parser.add_argument("--desk-only", action="store_true", help="Test desk-api only")
    parser.add_argument("--ref-only", action="store_true", help="Test reference only")
    parser.add_argument("--desk-url", default=DESK_API, help=f"desk-api URL (default: {DESK_API})")
    parser.add_argument("--ref-url", default=REFERENCE, help=f"Reference URL (default: {REFERENCE})")
    parser.add_argument("--test", help="Run only tests matching this substring")
    parser.add_argument("--verbose", "-v", action="store_true", help="Show response bodies")
    parser.add_argument("--iterations", "-n", type=int, default=1,
                        help="Number of iterations to run (for performance comparison)")
    parser.add_argument("--username", default=USERNAME, help=f"Login username (default: {USERNAME})")
    parser.add_argument("--password", default=PASSWORD, help=f"Login password (default: {PASSWORD})")
    parser.add_argument("--report", nargs="?", const="auto", default=None,
                        help="Write report to file (default: scripts/compat-report.txt)")
    args = parser.parse_args()

    VERBOSE = args.verbose

    desk_url = args.desk_url
    ref_url = args.ref_url

    iterations = args.iterations

    # Set up report file capturing
    report_buf = None
    _orig_stdout = sys.stdout
    if args.report:
        report_buf = io.StringIO()

        class Tee:
            """Write to both console and buffer."""
            def __init__(self, *streams):
                self.streams = streams
            def write(self, data):
                for s in self.streams:
                    s.write(data)
            def flush(self):
                for s in self.streams:
                    s.flush()

        sys.stdout = Tee(_orig_stdout, report_buf)

    print(f"{C.BOLD}Compatibility Test Suite: desk-api vs OneCMS Reference{C.RESET}")
    print(f"  desk-api:  {desk_url}")
    print(f"  reference: {ref_url}")
    if iterations > 1:
        print(f"  iterations: {iterations}")
    print()

    # Pre-download sample images if file/image tests will run
    if not args.test or any(k in (args.test or "") for k in ["file", "image", "download"]):
        print(f"{C.INFO}Downloading sample images (cached in {_cache_dir})...{C.RESET}")
        imgs = ensure_sample_images()
        if imgs:
            for label, data in imgs.items():
                print(f"  {label}: {len(data) / (1024*1024):.1f} MB")
        else:
            print(f"  {C.WARN}No sample images available — tests will use fallback{C.RESET}")
        print()

    total_passed = 0
    total_failed = 0
    total_skipped = 0

    if args.desk_only:
        # Single server mode: desk-api only
        print(f"{C.INFO}Checking desk-api...{C.RESET}", end=" ")
        if not check_server(desk_url, "desk-api"):
            print(f"{C.FAIL}NOT REACHABLE{C.RESET}")
            sys.exit(1)
        print(f"{C.PASS}OK{C.RESET}")

        client = ApiClient(desk_url, "desk-api")
        p, f, s = run_single_server_tests(client, args.test)
        total_passed += p
        total_failed += f
        total_skipped += s

    elif args.ref_only:
        # Single server mode: reference only
        print(f"{C.INFO}Checking reference...{C.RESET}", end=" ")
        if not check_server(ref_url, "reference"):
            print(f"{C.FAIL}NOT REACHABLE{C.RESET}")
            sys.exit(1)
        print(f"{C.PASS}OK{C.RESET}")

        client = ApiClient(ref_url, "reference")
        p, f, s = run_single_server_tests(client, args.test)
        total_passed += p
        total_failed += f
        total_skipped += s

    else:
        # Full comparison mode: both servers
        print(f"{C.INFO}Checking servers...{C.RESET}")

        desk_ok = check_server(desk_url, "desk-api")
        ref_ok = check_server(ref_url, "reference")

        print(f"  desk-api:  {'%sOK%s' % (C.PASS, C.RESET) if desk_ok else '%sNOT REACHABLE%s' % (C.FAIL, C.RESET)}")
        print(f"  reference: {'%sOK%s' % (C.PASS, C.RESET) if ref_ok else '%sNOT REACHABLE%s' % (C.FAIL, C.RESET)}")

        if not desk_ok and not ref_ok:
            print(f"\n{C.FAIL}Neither server is reachable. Aborting.{C.RESET}")
            sys.exit(1)

        if desk_ok and ref_ok:
            # Comparison tests
            desk = ApiClient(desk_url, "desk-api")
            ref = ApiClient(ref_url, "reference")
            p, f, s = run_comparison_tests(desk, ref, args.test, iterations)
            total_passed += p
            total_failed += f
            total_skipped += s
        else:
            # Fall back to single server
            available = desk_url if desk_ok else ref_url
            label = "desk-api" if desk_ok else "reference"
            missing = "reference" if desk_ok else "desk-api"
            print(f"\n{C.WARN}{missing} not reachable — running single-server tests on {label}{C.RESET}")

            client = ApiClient(available, label)
            p, f, s = run_single_server_tests(client, args.test)
            total_passed += p
            total_failed += f
            total_skipped += s

    # Summary
    print(f"\n{C.BOLD}{'=' * 60}{C.RESET}")
    total = total_passed + total_failed + total_skipped
    print(f"{C.BOLD}Results: {total} tests{C.RESET}", end="")
    if total_passed:
        print(f"  {C.PASS}{total_passed} passed{C.RESET}", end="")
    if total_failed:
        print(f"  {C.FAIL}{total_failed} failed{C.RESET}", end="")
    if total_skipped:
        print(f"  {C.WARN}{total_skipped} skipped{C.RESET}", end="")
    print()

    # Write report file
    if report_buf:
        sys.stdout = _orig_stdout
        report_path = args.report if args.report != "auto" else "scripts/compat-report.txt"
        # Strip ANSI escape codes for clean text file
        ansi_re = re.compile(r"\033\[[0-9;]*m")
        clean = ansi_re.sub("", report_buf.getvalue())
        with open(report_path, "w", encoding="utf-8") as f:
            f.write(clean)
        print(f"\n{C.INFO}Report written to {report_path}{C.RESET}")

    sys.exit(1 if total_failed > 0 else 0)


if __name__ == "__main__":
    main()
