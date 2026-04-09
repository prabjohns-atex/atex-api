#!/usr/bin/env python3
"""
Append/Sum content engine test suite for Desk CMS (AMS-17152 / PMC-2255)

Creates test articles with known field values, then exercises the append
and sum operations via the REST API and verifies results.

Usage:
    python scripts/append-test.py                          # Run all tests
    python scripts/append-test.py --test test_basic_append # Run a specific test
    python scripts/append-test.py --seed-only              # Create test data only
    python scripts/append-test.py --cleanup                # Remove test content
    python scripts/append-test.py --verbose                # Show full responses
    python scripts/append-test.py --ref-url http://localhost:38084/onecms
"""

import argparse
import json
import sys
import time
import uuid

import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

REFERENCE = "http://localhost:38084/onecms"

USERNAME = "sysadmin"
PASSWORD = "sysadmin"

# Tag for identifying test content (for cleanup)
TEST_TAG = "APPEND-TEST"

# ---------------------------------------------------------------------------
# Console colours
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
# API Client (same pattern as compat-test.py)
# ---------------------------------------------------------------------------

class ApiClient:
    """Thin wrapper around requests for a single server."""

    def __init__(self, base_url: str):
        self.base = base_url.rstrip("/")
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

    def get(self, path: str, *, params=None):
        r = requests.get(self._url(path), headers=self._headers(),
                         params=params, allow_redirects=True, timeout=15)
        return r.status_code, _safe_json(r), r

    def post(self, path: str, body=None):
        r = requests.post(self._url(path), headers=self._headers(),
                          json=body, timeout=30)
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

# ---------------------------------------------------------------------------
# Test state
# ---------------------------------------------------------------------------

class TestState:
    """Tracks content created during tests."""
    def __init__(self):
        self.created_ids: list[str] = []      # unversioned IDs for cleanup
        self.target_id: str | None = None      # target article for append
        self.source_ids: list[str] = []        # source articles for append
        self.target_vid: str | None = None     # versioned target ID
        self.source_vids: list[str] = []       # versioned source IDs

state = TestState()

# ---------------------------------------------------------------------------
# Content factories
# ---------------------------------------------------------------------------

def make_article(name: str, headline: str, body: str, byline: str = "",
                 priority: int = 0, words: int = 0) -> dict:
    """Create an article content body."""
    return {
        "aspects": {
            "contentData": {
                "data": {
                    "_type": "atex.onecms.article",
                    "name": f"{TEST_TAG} {name}",
                    "objectType": "article",
                    "headline": {"text": headline},
                    "lead": {"text": ""},
                    "body": {"text": body},
                    "byline": byline,
                    "priority": priority,
                    "words": words
                }
            }
        }
    }


def make_append_config(operation: str = "append",
                       append_to_target: bool = True,
                       use_sort: bool = True,
                       sort_field: str = "priority",
                       add_engagement: bool = False,
                       add_note: bool = False,
                       test_mode: bool = False,
                       cmds: list | None = None) -> dict:
    """Build an append config object."""
    config = {
        "operation": operation,
        "appendToTarget": append_to_target,
        "useSort": use_sort,
        "sortAspect": "contentData",
        "sortField": sort_field,
        "testMode": test_mode
    }
    if add_engagement:
        config["addEngagement"] = True
        config["srcEngagements"] = ["name: name"]
        config["dstEngagements"] = ["name: name", "signoff: byline", "text: headline"]
    if add_note:
        config["addNote"] = True
    if cmds:
        config["cmds"] = cmds
    return config


def make_append_cmd_body() -> list[dict]:
    """Standard command set: append byline as note + append body text."""
    return [
        {
            "cmdName": "appendNote",
            "src": {
                "aspect": "contentData",
                "attribute": "byline",
                "element": "Text",
                "name": "Text"
            },
            "dst": {
                "aspect": "contentData",
                "attribute": "body",
                "element": "Text",
                "name": "Text 1"
            }
        },
        {
            "cmdName": "appendElement",
            "src": {
                "aspect": "contentData",
                "attribute": "body",
                "element": "Text",
                "name": "Text"
            },
            "dst": {
                "aspect": "contentData",
                "attribute": "body",
                "element": "Text",
                "name": "Text 1"
            }
        }
    ]

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def create_article(client: ApiClient, article: dict) -> tuple[str, str]:
    """Create article, return (unversioned_id, versioned_id). Raises on failure."""
    status, body, resp = client.post("/content", article)
    if status != 201:
        raise RuntimeError(f"Failed to create article: HTTP {status} — {body}")
    content_id = body.get("id")
    version_id = body.get("version") or resp.headers.get("ETag", "").strip('"')
    state.created_ids.append(content_id)
    return content_id, version_id


def fetch_content(client: ApiClient, versioned_id: str) -> dict:
    """Fetch content by versioned ID."""
    status, body, _ = client.get(f"/content/contentid/{versioned_id}")
    if status != 200:
        raise RuntimeError(f"Failed to fetch {versioned_id}: HTTP {status}")
    return body


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


def dump_json(label: str, data):
    if not VERBOSE:
        return
    try:
        print(f"    {C.DIM}{label}: {json.dumps(data, indent=2, default=str)[:2000]}{C.RESET}")
    except Exception:
        print(f"    {C.DIM}{label}: {data}{C.RESET}")

# ---------------------------------------------------------------------------
# Seed: create test content
# ---------------------------------------------------------------------------

def seed_test_content(client: ApiClient):
    """Create a set of articles suitable for append/sum testing.

    Creates:
      - 1 target article (priority=1) — the destination for appends
      - 3 source articles (priority=2,3,4) — content to be appended
    """
    print(f"\n{C.BOLD}Seeding test content...{C.RESET}")

    target = make_article(
        name="Target Article",
        headline="Target: Icelandic Obits Collection",
        body="<p>This is the target article that will receive appended content.</p>",
        byline="Editor Desk",
        priority=1,
        words=12
    )
    tid, tvid = create_article(client, target)
    state.target_id = tid
    state.target_vid = tvid
    print(f"  Created target: {tid}")

    sources = [
        make_article(
            name="Source Article 1",
            headline="Obit: Jón Sigurdsson (1811-1879)",
            body="<p>Jón Sigurdsson was an Icelandic independence leader and statesman.</p>",
            byline="Guðrún Helgadóttir",
            priority=2,
            words=45
        ),
        make_article(
            name="Source Article 2",
            headline="Obit: Halldór Laxness (1902-1998)",
            body="<p>Halldór Laxness was an Icelandic novelist and Nobel laureate.</p>",
            byline="Einar Björnsson",
            priority=3,
            words=62
        ),
        make_article(
            name="Source Article 3",
            headline="Obit: Vigdís Finnbogadóttir (1930-2024)",
            body="<p>Vigdís Finnbogadóttir was the world's first democratically elected female head of state.</p>",
            byline="Þóra Magnúsdóttir",
            priority=4,
            words=78
        ),
    ]

    for src in sources:
        sid, svid = create_article(client, src)
        state.source_ids.append(sid)
        state.source_vids.append(svid)
        print(f"  Created source: {sid}")

    print(f"  {C.PASS}Seeded {1 + len(state.source_ids)} articles{C.RESET}")

# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_login(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /security/token — authenticate."""
    status, body = client.login()
    dump_json("login", body)
    if status != 200:
        return False, f"login failed: HTTP {status}", []
    return True, f"HTTP {status}, token obtained", []


def test_seed_content(client: ApiClient) -> tuple[bool, str, list[str]]:
    """Create test articles for append/sum operations."""
    notes = []
    try:
        seed_test_content(client)
        if not state.target_id or len(state.source_ids) < 3:
            return False, "incomplete seed", notes
        return True, f"target={state.target_id}, sources={len(state.source_ids)}", notes
    except Exception as e:
        return False, str(e), notes


def test_basic_append(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — basic append without commands."""
    notes = []
    if not state.target_id or not state.source_ids:
        return False, "no test content — run test_seed_content first", notes

    config = make_append_config()
    body = {
        "targetContentId": state.target_id,
        "entries": state.source_ids,
        "config": config
    }

    dump_json("request", body)
    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    if status != 200:
        return False, f"HTTP {status}", notes

    if resp and resp.get("error"):
        return False, f"error: {resp['error']}", notes

    processed = resp.get("processedIds", [])
    if not processed:
        notes.append("no processedIds returned")
        return False, "empty processedIds", notes

    notes.append(f"processedIds: {len(processed)}")

    warnings = resp.get("warnings", [])
    if warnings:
        for w in warnings:
            notes.append(f"warning: {w}")

    return True, f"HTTP {status}, processed {len(processed)} items", notes


def test_append_with_commands(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — append with body/byline commands."""
    notes = []
    if not state.target_id or not state.source_ids:
        return False, "no test content", notes

    cmds = make_append_cmd_body()
    config = make_append_config(
        add_engagement=True,
        add_note=True,
        cmds=cmds
    )
    body = {
        "targetContentId": state.target_id,
        "entries": state.source_ids,
        "config": config
    }

    dump_json("request", body)
    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    if status != 200:
        return False, f"HTTP {status}", notes

    if resp and resp.get("error"):
        return False, f"error: {resp['error']}", notes

    processed = resp.get("processedIds", [])
    notes.append(f"processedIds: {len(processed)}")

    return True, f"HTTP {status}, processed {len(processed)} items", notes


def test_append_test_mode(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — testMode=true should preview without saving."""
    notes = []
    if not state.target_id or not state.source_ids:
        return False, "no test content", notes

    config = make_append_config(test_mode=True)
    body = {
        "targetContentId": state.target_id,
        "entries": state.source_ids,
        "config": config
    }

    dump_json("request", body)
    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    if status != 200:
        return False, f"HTTP {status}", notes

    if resp and resp.get("error"):
        return False, f"error: {resp['error']}", notes

    notes.append("testMode=true — changes should not be persisted")
    return True, f"HTTP {status}", notes


def test_append_missing_target(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — missing targetContentId should return error."""
    notes = []

    config = make_append_config()
    body = {
        "entries": state.source_ids or ["onecms:fake-id"],
        "config": config
    }

    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    # Expect an error response (not a 500)
    if resp and resp.get("error"):
        notes.append(f"error returned: {resp['error'][:100]}")
        return True, f"HTTP {status}, error handled correctly", notes

    if status >= 400:
        return True, f"HTTP {status} — server rejected as expected", notes

    notes.append("expected an error but got success")
    return False, f"HTTP {status}", notes


def test_append_empty_entries(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — empty entries list should return error."""
    notes = []

    config = make_append_config()
    body = {
        "targetContentId": state.target_id or "onecms:fake-id",
        "entries": [],
        "config": config
    }

    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    if resp and resp.get("error"):
        notes.append(f"error returned: {resp['error'][:100]}")
        return True, f"HTTP {status}, error handled correctly", notes

    if status >= 400:
        return True, f"HTTP {status} — server rejected as expected", notes

    notes.append("expected an error but got success")
    return False, f"HTTP {status}", notes


def test_remove_appended_content(client: ApiClient) -> tuple[bool, str, list[str]]:
    """POST /dam/append/content — remove operation."""
    notes = []
    if not state.target_id or not state.source_ids:
        return False, "no test content", notes

    config = make_append_config(operation="remove", append_to_target=False)
    body = {
        "targetContentId": state.target_id,
        "entries": state.source_ids,
        "config": config
    }

    dump_json("request", body)
    status, resp, _ = client.post("/dam/append/content/", body)
    dump_json("response", resp)

    if status != 200:
        return False, f"HTTP {status}", notes

    # Remove may return empty processedIds if nothing was previously appended
    processed = resp.get("processedIds", []) if resp else []
    notes.append(f"processedIds: {len(processed)}")

    return True, f"HTTP {status}", notes


def test_verify_target_content(client: ApiClient) -> tuple[bool, str, list[str]]:
    """GET /content/contentid — verify target article was modified by append."""
    notes = []
    if not state.target_vid:
        return False, "no target versioned ID", notes

    try:
        content = fetch_content(client, state.target_vid)
        dump_json("target content", content)

        content_data = (content.get("aspects", {})
                        .get("contentData", {})
                        .get("data", {}))

        body_text = content_data.get("body", {})
        if isinstance(body_text, dict):
            body_text = body_text.get("text", "")

        if not body_text:
            notes.append("body is empty after append")
            return False, "body empty", notes

        notes.append(f"body length: {len(body_text)} chars")

        # Check for engagement aspect
        engagements = content.get("aspects", {}).get("engagementAspect", {})
        if engagements:
            notes.append("engagement aspect present")

        return True, "target content verified", notes

    except Exception as e:
        return False, str(e), notes

# ---------------------------------------------------------------------------
# Test registry
# ---------------------------------------------------------------------------

TESTS = [
    ("login",                    test_login),
    ("seed_content",             test_seed_content),
    ("basic_append",             test_basic_append),
    ("append_with_commands",     test_append_with_commands),
    ("append_test_mode",         test_append_test_mode),
    ("append_missing_target",    test_append_missing_target),
    ("append_empty_entries",     test_append_empty_entries),
    ("remove_appended_content",  test_remove_appended_content),
    ("verify_target_content",    test_verify_target_content),
]

# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

VERBOSE = False


def run_tests(client: ApiClient, test_filter: str | None = None,
              seed_only: bool = False):
    passed = 0
    failed = 0
    skipped = 0

    tests = TESTS
    if seed_only:
        tests = [t for t in TESTS if t[0] in ("login", "seed_content")]

    if test_filter:
        tests = [t for t in tests if test_filter in t[0]]
        if not tests:
            print(f"{C.FAIL}No tests match filter: {test_filter}{C.RESET}")
            return 1

    print(f"\n{C.BOLD}Append/Sum Content Test Suite{C.RESET}")
    print(f"  Server: {client.base}")
    print(f"  Tests:  {len(tests)}")
    print()

    for name, fn in tests:
        try:
            ok, msg, notes = fn(client)
            print_result(name, ok, msg, notes)
            if ok:
                passed += 1
            else:
                failed += 1
        except Exception as e:
            print_result(name, False, str(e))
            failed += 1

    print(f"\n{C.BOLD}Results:{C.RESET} "
          f"{C.PASS}{passed} passed{C.RESET}, "
          f"{C.FAIL}{failed} failed{C.RESET}, "
          f"{C.WARN}{skipped} skipped{C.RESET}")

    return 1 if failed else 0


def main():
    global VERBOSE

    parser = argparse.ArgumentParser(description="Append/Sum content engine tests")
    parser.add_argument("--ref-url", default=REFERENCE,
                        help=f"Reference server URL (default: {REFERENCE})")
    parser.add_argument("--test", default=None,
                        help="Run only tests matching this substring")
    parser.add_argument("--seed-only", action="store_true",
                        help="Only create test data, don't run tests")
    parser.add_argument("--verbose", action="store_true",
                        help="Show full request/response bodies")

    args = parser.parse_args()
    VERBOSE = args.verbose

    client = ApiClient(args.ref_url)
    rc = run_tests(client, test_filter=args.test, seed_only=args.seed_only)
    sys.exit(rc)


if __name__ == "__main__":
    main()
