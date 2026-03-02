#!/usr/bin/env python3
"""
Legacy Polopoly Content Migration Script

Fetches legacy content from the reference OneCMS server and migrates it
to desk-api with both externalId and policyId aliases so all existing
references resolve.

Usage:
    python scripts/migrate-legacy.py                       # Full migration
    python scripts/migrate-legacy.py --discover            # List what needs migrating
    python scripts/migrate-legacy.py --verify              # Verify migration
    python scripts/migrate-legacy.py --skip-sections       # Skip section entries
    python scripts/migrate-legacy.py --filter "p.onecms"   # Migrate matching prefix
    python scripts/migrate-legacy.py --dry-run             # Show what would happen
    python scripts/migrate-legacy.py --verbose             # Show full responses
"""

import argparse
import json
import os
import sys
import time

import requests

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DESK_API = "http://localhost:8081"
REFERENCE = "http://localhost:38084/onecms"

USERNAME = "sysadmin"
PASSWORD = "sysadmin"

# External IDs served by ConfigurationService (classpath resources).
# Loaded from scripts/config-mapping.txt — these don't need migration.
CONFIG_EXTERNAL_IDS = set()


def load_config_external_ids():
    """Parse config-mapping.txt and extract all external IDs that desk-api
    already serves from classpath resources via ConfigurationService."""
    mapping_file = os.path.join(os.path.dirname(__file__), "config-mapping.txt")
    ids = set()
    try:
        with open(mapping_file, "r") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.split("|")
                if len(parts) >= 6:
                    external_id = parts[5].strip()
                    if external_id:
                        ids.add(external_id)
    except FileNotFoundError:
        pass
    return ids


CONFIG_EXTERNAL_IDS = load_config_external_ids()
remove
# ---------------------------------------------------------------------------
# Console colours
# ---------------------------------------------------------------------------

class C:
    OK    = "\033[92m"   # green
    FAIL  = "\033[91m"   # red
    WARN  = "\033[93m"   # yellow
    INFO  = "\033[94m"   # blue
    DIM   = "\033[90m"   # grey
    BOLD  = "\033[1m"
    RESET = "\033[0m"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def login(base_url):
    """Login and return auth token."""
    resp = requests.post(
        f"{base_url}/security/token",
        json={"username": USERNAME, "password": PASSWORD},
        timeout=10
    )
    resp.raise_for_status()
    return resp.json()["token"]


def get_content_by_external_id(base_url, token, ext_id):
    """Fetch content by external ID, following redirects."""
    headers = {"X-Auth-Token": token}
    resp = requests.get(
        f"{base_url}/content/externalid/{ext_id}",
        headers=headers,
        allow_redirects=True,
        timeout=30
    )
    return resp


def get_content_by_id(base_url, token, content_id):
    """Fetch content by content ID, following redirects."""
    headers = {"X-Auth-Token": token}
    resp = requests.get(
        f"{base_url}/content/contentid/{content_id}",
        headers=headers,
        allow_redirects=True,
        timeout=30
    )
    return resp


def desk_already_has(desk_token, ext_id):
    """Check if desk-api already serves this external ID."""
    try:
        resp = get_content_by_external_id(DESK_API, desk_token, ext_id)
        return resp.status_code == 200
    except Exception:
        return False


def discover_external_ids_from_reference(ref_token, known_ids):
    """
    Use the reference server's search or direct external ID list.
    Since we don't have direct DB access, we'll try fetching a known list
    of external IDs from the reference server. If a list file exists, use that.
    Otherwise, we scan common prefixes.
    """
    # Try loading from a local file first (pre-extracted from DB)
    try:
        with open("scripts/external-ids.txt", "r") as f:
            ids = [line.strip() for line in f if line.strip() and not line.startswith("#")]
            if ids:
                return ids
    except FileNotFoundError:
        pass

    print(f"{C.WARN}No scripts/external-ids.txt found.{C.RESET}")
    print(f"{C.INFO}To generate, run against the reference DB:{C.RESET}")
    print(f"{C.DIM}  mysql -h localhost -P 33306 -u desk -pdesk desk "
          f"-e \"SELECT value FROM idaliases ia JOIN aliases a ON ia.aliasid=a.aliasid "
          f"WHERE a.name='externalId' ORDER BY value\" -B -N > scripts/external-ids.txt{C.RESET}")
    print()

    # Fallback: try scanning common prefixes via the reference API
    prefixes = [
        "p.onecms.", "p.ContentType.", "p.Nosql", "p.DefaultTemplate",
        "atex.configuration.desk.", "atex.dam.", "atex.plugins.",
        "dam.wfstatuslist.", "dam.webstatuslist.",
        "com.atex.onecms.", "com.polopoly.",
        "plugins.", "dimension.",
    ]

    discovered = []
    headers = {"X-Auth-Token": ref_token}

    for prefix in prefixes:
        # Try a few known IDs under each prefix
        for suffix in ["", "d", "configuration", "standard", "default"]:
            test_id = prefix + suffix if suffix else prefix.rstrip(".")
            try:
                resp = requests.get(
                    f"{REFERENCE}/content/externalid/{test_id}",
                    headers=headers,
                    allow_redirects=True,
                    timeout=10
                )
                if resp.status_code == 200:
                    discovered.append(test_id)
            except Exception:
                pass

    return discovered


def extract_policy_id(content_json):
    """
    Extract the policy:X.Y ID from the content response.
    The 'id' field in the response is in format 'delegationId:key',
    e.g. 'policy:2.184'.
    """
    if content_json and "id" in content_json:
        return content_json["id"]
    return None


def migrate_one(desk_token, ext_id, content_json, policy_id, dry_run=False, verbose=False):
    """Migrate a single content item to desk-api."""
    aspects = content_json.get("aspects", {})

    aliases = [{"namespace": "externalId", "value": ext_id}]
    if policy_id:
        aliases.append({"namespace": "policyId", "value": policy_id})

    payload = {
        "aspects": aspects,
        "aliases": aliases
    }

    if dry_run:
        alias_str = ", ".join(f"{a['namespace']}={a['value']}" for a in aliases)
        aspect_count = len(aspects)
        print(f"  {C.DIM}[dry-run] Would create: {aspect_count} aspects, aliases: {alias_str}{C.RESET}")
        return True

    headers = {
        "X-Auth-Token": desk_token,
        "Content-Type": "application/json"
    }

    try:
        resp = requests.post(
            f"{DESK_API}/admin/migrate/content",
            headers=headers,
            json=payload,
            timeout=30
        )

        if resp.status_code == 201:
            if verbose:
                result = resp.json()
                print(f"  {C.DIM}Created: {result.get('id', '?')}{C.RESET}")
            return True
        elif resp.status_code == 409:
            if verbose:
                print(f"  {C.DIM}Already exists (skipped){C.RESET}")
            return True  # Already migrated
        else:
            print(f"  {C.FAIL}Failed ({resp.status_code}): {resp.text[:200]}{C.RESET}")
            return False
    except Exception as e:
        print(f"  {C.FAIL}Error: {e}{C.RESET}")
        return False


def verify_one(desk_token, ext_id, policy_id, verbose=False):
    """Verify a migrated content item is accessible via both aliases."""
    ok = True

    # Check external ID
    resp = get_content_by_external_id(DESK_API, desk_token, ext_id)
    if resp.status_code == 200:
        if verbose:
            print(f"  {C.OK}externalId OK{C.RESET}")
    else:
        print(f"  {C.FAIL}externalId FAIL ({resp.status_code}){C.RESET}")
        ok = False

    # Check policy ID
    if policy_id:
        resp = get_content_by_id(DESK_API, desk_token, policy_id)
        if resp.status_code == 200:
            if verbose:
                print(f"  {C.OK}policyId OK{C.RESET}")
        else:
            print(f"  {C.FAIL}policyId FAIL ({resp.status_code}){C.RESET}")
            ok = False

    return ok

# ---------------------------------------------------------------------------
# Main commands
# ---------------------------------------------------------------------------

def cmd_discover(args):
    """Discover what needs migrating."""
    print(f"{C.BOLD}Discovering legacy content...{C.RESET}")
    print()

    ref_token = login(REFERENCE)
    desk_token = login(DESK_API)

    ext_ids = discover_external_ids_from_reference(ref_token, set())
    if not ext_ids:
        print(f"{C.WARN}No external IDs found. See instructions above.{C.RESET}")
        return

    # Filter
    if args.filter:
        ext_ids = [eid for eid in ext_ids if args.filter in eid]

    if args.skip_sections:
        before = len(ext_ids)
        ext_ids = [eid for eid in ext_ids if not eid.startswith("section")]
        print(f"{C.DIM}Skipped {before - len(ext_ids)} section entries{C.RESET}")

    # Skip IDs already served by ConfigurationService (from config-mapping.txt)
    if CONFIG_EXTERNAL_IDS:
        before = len(ext_ids)
        ext_ids = [eid for eid in ext_ids if eid not in CONFIG_EXTERNAL_IDS]
        skipped_cfg = before - len(ext_ids)
        if skipped_cfg:
            print(f"{C.DIM}Skipped {skipped_cfg} config entries (served by ConfigurationService){C.RESET}")

    print(f"Found {len(ext_ids)} external IDs to check")
    print()

    needs_migration = []
    already_available = []
    not_on_ref = []

    for i, ext_id in enumerate(ext_ids):
        if (i + 1) % 50 == 0:
            print(f"{C.DIM}  Checked {i + 1}/{len(ext_ids)}...{C.RESET}")

        # Check if desk-api already has it
        if desk_already_has(desk_token, ext_id):
            already_available.append(ext_id)
            continue

        # Check if reference has it
        resp = get_content_by_external_id(REFERENCE, ref_token, ext_id)
        if resp.status_code == 200:
            policy_id = extract_policy_id(resp.json())
            needs_migration.append((ext_id, policy_id))
        else:
            not_on_ref.append(ext_id)

    print()
    print(f"{C.BOLD}Results:{C.RESET}")
    print(f"  {C.OK}Already available on desk-api: {len(already_available)}{C.RESET}")
    print(f"  {C.WARN}Needs migration: {len(needs_migration)}{C.RESET}")
    print(f"  {C.DIM}Not found on reference: {len(not_on_ref)}{C.RESET}")

    if needs_migration and args.verbose:
        print()
        print(f"{C.BOLD}Items needing migration:{C.RESET}")
        # Group by prefix
        prefixes = {}
        for ext_id, policy_id in needs_migration:
            prefix = ext_id.split(".")[0] if "." in ext_id else ext_id.split(":")[0]
            prefixes.setdefault(prefix, []).append(ext_id)

        for prefix in sorted(prefixes.keys()):
            items = prefixes[prefix]
            print(f"  {prefix}: {len(items)}")
            if args.verbose:
                for item in items[:5]:
                    print(f"    {C.DIM}{item}{C.RESET}")
                if len(items) > 5:
                    print(f"    {C.DIM}... and {len(items) - 5} more{C.RESET}")


def cmd_migrate(args):
    """Run the migration."""
    print(f"{C.BOLD}Legacy Content Migration{C.RESET}")
    if args.dry_run:
        print(f"{C.WARN}DRY RUN — no changes will be made{C.RESET}")
    print()

    ref_token = login(REFERENCE)
    desk_token = login(DESK_API)

    # Load external IDs
    ext_ids = discover_external_ids_from_reference(ref_token, set())
    if not ext_ids:
        print(f"{C.WARN}No external IDs found. See instructions above.{C.RESET}")
        return

    # Filter
    if args.filter:
        ext_ids = [eid for eid in ext_ids if args.filter in eid]
        print(f"Filtered to {len(ext_ids)} IDs matching '{args.filter}'")

    if args.skip_sections:
        before = len(ext_ids)
        ext_ids = [eid for eid in ext_ids if not eid.startswith("section")]
        skipped = before - len(ext_ids)
        if skipped:
            print(f"{C.DIM}Skipped {skipped} section entries{C.RESET}")

    # Skip IDs already served by ConfigurationService (from config-mapping.txt)
    if CONFIG_EXTERNAL_IDS:
        before = len(ext_ids)
        ext_ids = [eid for eid in ext_ids if eid not in CONFIG_EXTERNAL_IDS]
        skipped_cfg = before - len(ext_ids)
        if skipped_cfg:
            print(f"{C.DIM}Skipped {skipped_cfg} config entries (served by ConfigurationService){C.RESET}")

    print(f"Processing {len(ext_ids)} external IDs...")
    print()

    migrated = 0
    skipped = 0
    failed = 0
    not_found = 0

    for i, ext_id in enumerate(ext_ids):
        # Progress
        if (i + 1) % 25 == 0 or i == 0:
            print(f"{C.DIM}[{i + 1}/{len(ext_ids)}] "
                  f"migrated={migrated} skipped={skipped} failed={failed}{C.RESET}")

        # Skip if desk-api already has it
        if not args.dry_run and desk_already_has(desk_token, ext_id):
            skipped += 1
            if args.verbose:
                print(f"  {C.DIM}SKIP {ext_id} (already available){C.RESET}")
            continue

        # Fetch from reference
        try:
            resp = get_content_by_external_id(REFERENCE, ref_token, ext_id)
        except Exception as e:
            print(f"  {C.FAIL}FETCH ERROR {ext_id}: {e}{C.RESET}")
            failed += 1
            continue

        if resp.status_code != 200:
            if args.verbose:
                print(f"  {C.DIM}NOT FOUND on reference: {ext_id} ({resp.status_code}){C.RESET}")
            not_found += 1
            continue

        content_json = resp.json()
        policy_id = extract_policy_id(content_json)

        if args.verbose:
            aspect_names = list(content_json.get("aspects", {}).keys())
            print(f"  {C.INFO}MIGRATE {ext_id} → {policy_id or '?'} "
                  f"({len(aspect_names)} aspects){C.RESET}")

        if migrate_one(desk_token, ext_id, content_json, policy_id,
                       dry_run=args.dry_run, verbose=args.verbose):
            migrated += 1
        else:
            failed += 1

        # Brief pause to avoid overwhelming servers
        if not args.dry_run and (i + 1) % 100 == 0:
            time.sleep(0.5)

    print()
    print(f"{C.BOLD}Migration complete:{C.RESET}")
    print(f"  {C.OK}Migrated: {migrated}{C.RESET}")
    print(f"  {C.DIM}Skipped (already present): {skipped}{C.RESET}")
    print(f"  {C.DIM}Not found on reference: {not_found}{C.RESET}")
    if failed:
        print(f"  {C.FAIL}Failed: {failed}{C.RESET}")


def cmd_verify(args):
    """Verify migrated content is accessible."""
    print(f"{C.BOLD}Verifying migration...{C.RESET}")
    print()

    desk_token = login(DESK_API)
    ref_token = login(REFERENCE)

    ext_ids = discover_external_ids_from_reference(ref_token, set())
    if not ext_ids:
        print(f"{C.WARN}No external IDs found.{C.RESET}")
        return

    if args.filter:
        ext_ids = [eid for eid in ext_ids if args.filter in eid]

    if args.skip_sections:
        ext_ids = [eid for eid in ext_ids if not eid.startswith("section")]

    # Skip IDs already served by ConfigurationService (from config-mapping.txt)
    if CONFIG_EXTERNAL_IDS:
        ext_ids = [eid for eid in ext_ids if eid not in CONFIG_EXTERNAL_IDS]

    ok = 0
    fail = 0

    for i, ext_id in enumerate(ext_ids):
        if (i + 1) % 50 == 0:
            print(f"{C.DIM}  Verified {i + 1}/{len(ext_ids)}...{C.RESET}")

        # Get the policy ID from reference for verification
        policy_id = None
        try:
            resp = get_content_by_external_id(REFERENCE, ref_token, ext_id)
            if resp.status_code == 200:
                policy_id = extract_policy_id(resp.json())
        except Exception:
            pass

        if verify_one(desk_token, ext_id, policy_id, verbose=args.verbose):
            ok += 1
        else:
            fail += 1
            print(f"  {C.FAIL}VERIFY FAIL: {ext_id}{C.RESET}")

    print()
    print(f"{C.BOLD}Verification results:{C.RESET}")
    print(f"  {C.OK}Passed: {ok}{C.RESET}")
    if fail:
        print(f"  {C.FAIL}Failed: {fail}{C.RESET}")
    else:
        print(f"  {C.OK}All content accessible!{C.RESET}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Migrate legacy Polopoly content from reference OneCMS to desk-api"
    )

    parser.add_argument("--discover", action="store_true",
                       help="List what needs migrating without making changes")
    parser.add_argument("--verify", action="store_true",
                       help="Verify that migrated content is accessible")
    parser.add_argument("--dry-run", action="store_true",
                       help="Show what would be migrated without making changes")
    parser.add_argument("--skip-sections", action="store_true",
                       help="Skip 'section*' external IDs (6400+ entries)")
    parser.add_argument("--include-sections", action="store_true",
                       help="Include section entries (overrides --skip-sections)")
    parser.add_argument("--filter", type=str, default=None,
                       help="Only process external IDs containing this string")
    parser.add_argument("--verbose", action="store_true",
                       help="Show detailed output")
    parser.add_argument("--desk-url", type=str, default=DESK_API,
                       help=f"desk-api base URL (default: {DESK_API})")
    parser.add_argument("--ref-url", type=str, default=REFERENCE,
                       help=f"Reference server base URL (default: {REFERENCE})")

    args = parser.parse_args()

    global DESK_API, REFERENCE
    DESK_API = args.desk_url
    REFERENCE = args.ref_url

    if args.include_sections:
        args.skip_sections = False

    try:
        if args.discover:
            cmd_discover(args)
        elif args.verify:
            cmd_verify(args)
        else:
            cmd_migrate(args)
    except requests.exceptions.ConnectionError as e:
        print(f"\n{C.FAIL}Connection error: {e}{C.RESET}")
        print(f"{C.DIM}Make sure both servers are running:{C.RESET}")
        print(f"  desk-api: {DESK_API}")
        print(f"  reference: {REFERENCE}")
        sys.exit(1)
    except KeyboardInterrupt:
        print(f"\n{C.WARN}Interrupted{C.RESET}")
        sys.exit(130)


if __name__ == "__main__":
    main()
