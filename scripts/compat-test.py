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
"""

import argparse
import json
import sys
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
        r = requests.get(self._url(path), headers=self._headers(extra_headers),
                         params=params, allow_redirects=follow, timeout=15)
        return r.status_code, _safe_json(r), r

    def post(self, path: str, body=None, *, extra_headers=None):
        r = requests.post(self._url(path), headers=self._headers(extra_headers),
                          json=body, timeout=15)
        return r.status_code, _safe_json(r), r

    def put(self, path: str, body=None, *, if_match=None, extra_headers=None):
        h = extra_headers or {}
        if if_match:
            h["If-Match"] = _quote_etag(if_match)
        r = requests.put(self._url(path), headers=self._headers(h),
                         json=body, timeout=15)
        return r.status_code, _safe_json(r), r

    def delete(self, path: str, *, if_match=None, extra_headers=None):
        h = extra_headers or {}
        if if_match:
            h["If-Match"] = _quote_etag(if_match)
        r = requests.delete(self._url(path), headers=self._headers(h),
                            timeout=15)
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

    if ds not in (200, 302):
        notes.append(f"desk-api returned {ds}")
    if rs not in (200, 302):
        notes.append(f"reference returned {rs}")

    # If both return 200 with JSON, compare structure
    if ds == 200 and rs == 200 and db and rb:
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)
    elif ds == 200 and rs == 302:
        notes.append("desk returns config directly (200), reference returns redirect (302)")
    elif ds == 302 and rs == 200:
        notes.append("desk returns redirect (302), reference returns config directly (200)")

    passed = ds in (200, 302) and rs in (200, 302)
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


def test_dam_content(desk: ApiClient, ref: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /dam/content/contentid/{id} — DAM content endpoint."""
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

    # Give indexing a moment
    time.sleep(0.5)

    ds, db, _ = desk.get(f"/dam/content/contentid/{desk_id}")
    rs, rb, _ = ref.get(f"/dam/content/contentid/{ref_id}")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api DAM get returned {ds}")
    if rs != 200:
        notes.append(f"reference DAM get returned {rs}")

    if ds == 200 and rs == 200 and db and rb:
        diffs = compare_keys(db, rb, "response")
        for d in diffs:
            notes.append(d)

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

    desk_ok = ds in (200, 302)
    ref_ok = rs in (200, 302)

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
    """GET /dam/content/configuration/deskcfg — desk configuration."""
    notes = []

    ds, db, _ = desk.get("/dam/content/configuration/deskcfg")
    rs, rb, _ = ref.get("/dam/content/configuration/deskcfg")

    dump_json("desk", db)
    dump_json("ref", rb)

    if ds != 200:
        notes.append(f"desk-api returned {ds}")
    if rs != 200:
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

    passed = ds == 200 and rs == 200
    return passed, f"desk={ds} ref={rs}", notes


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
    ("DAM content (GET /dam/content/contentid/{id})", test_dam_content),
    ("DAM configuration (GET /dam/content/configuration/deskcfg)", test_dam_configuration),
    ("Principals me (GET /principals/users/me)", test_principals_me),
    ("Workspace CRUD flow", test_workspace_flow),
    ("Changes feed (GET /changes)", test_changes_feed),
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


def run_comparison_tests(desk: ApiClient, ref: ApiClient, test_filter: str | None = None):
    """Run all comparison tests between desk-api and reference."""
    print_section("Comparison Tests (desk-api vs reference)")

    passed = 0
    failed = 0
    skipped = 0

    for name, fn in ALL_COMPARISON_TESTS:
        if test_filter and test_filter not in fn.__name__:
            continue
        try:
            ok, msg, notes = fn(desk, ref)
            print_result(name, ok, msg, notes)
            if ok:
                passed += 1
            else:
                failed += 1
        except requests.exceptions.ConnectionError as e:
            print_skip(name, f"connection error: {e}")
            skipped += 1
        except Exception as e:
            print_result(name, False, f"exception: {e}")
            if VERBOSE:
                traceback.print_exc()
            failed += 1

    return passed, failed, skipped


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
    parser.add_argument("--username", default=USERNAME, help=f"Login username (default: {USERNAME})")
    parser.add_argument("--password", default=PASSWORD, help=f"Login password (default: {PASSWORD})")
    args = parser.parse_args()

    VERBOSE = args.verbose

    desk_url = args.desk_url
    ref_url = args.ref_url

    print(f"{C.BOLD}Compatibility Test Suite: desk-api vs OneCMS Reference{C.RESET}")
    print(f"  desk-api:  {desk_url}")
    print(f"  reference: {ref_url}")
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
            p, f, s = run_comparison_tests(desk, ref, args.test)
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

    sys.exit(1 if total_failed > 0 else 0)


if __name__ == "__main__":
    main()
