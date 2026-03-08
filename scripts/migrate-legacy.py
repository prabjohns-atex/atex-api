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
PASSWORD = os.environ.get("DESK_PASSWORD", "sysadmin")

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


def load_skip_ids():
    """Load external IDs to skip from scripts/migrate-skip.txt."""
    skip_file = os.path.join(os.path.dirname(__file__), "migrate-skip.txt")
    ids = set()
    try:
        with open(skip_file, "r") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    ids.add(line)
    except FileNotFoundError:
        pass
    return ids


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
        timeout=30
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


def load_external_ids():
    """
    Load external IDs from scripts/external-ids.txt (generated from MySQL).

    File format is tab-delimited: externalid\\tmajor\\tminor
    Generated via:
        docker exec adm-mpp-db-server-1 mysql -u desk -pdesk desk -B -N \\
            -e "SELECT externalid, major, minor FROM externalids ORDER BY externalid" \\
            > scripts/external-ids.txt

    Returns list of (externalid, policy_id) tuples where policy_id is "policy:major.minor".
    """
    ids_file = os.path.join(os.path.dirname(__file__), "external-ids.txt")
    entries = []
    try:
        with open(ids_file, "r") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.split("\t")
                if len(parts) >= 3:
                    ext_id = parts[0]
                    major = parts[1]
                    minor = parts[2]
                    policy_id = f"policy:{major}.{minor}"
                    entries.append((ext_id, policy_id))
                elif len(parts) == 1:
                    # Plain external ID (no major/minor)
                    entries.append((parts[0], None))
    except FileNotFoundError:
        print(f"{C.WARN}No scripts/external-ids.txt found.{C.RESET}")
        print(f"{C.INFO}To generate, run:{C.RESET}")
        print(f'{C.DIM}  docker exec adm-mpp-db-server-1 mysql -u desk -pdesk desk -B -N \\'
              f'\n    -e "SELECT externalid, major, minor FROM externalids ORDER BY externalid" \\'
              f"\n    > scripts/external-ids.txt{C.RESET}")
        print()
    return entries


def migrate_one(desk_token, ext_id, content_json, policy_id, dry_run=False, verbose=False):
    """Migrate a single content item to desk-api via POST /content."""
    aspects = content_json.get("aspects", {})

    operations = [{"type": "SetAliasOperation", "namespace": "externalId", "value": ext_id}]
    if policy_id:
        operations.append({"type": "SetAliasOperation", "namespace": "policyId", "value": policy_id})

    payload = {
        "aspects": aspects,
        "operations": operations
    }

    if dry_run:
        op_str = ", ".join(f"{o['namespace']}={o['value']}" for o in operations)
        aspect_count = len(aspects)
        print(f"  {C.DIM}[dry-run] Would create: {aspect_count} aspects, aliases: {op_str}{C.RESET}")
        return True

    headers = {
        "X-Auth-Token": desk_token,
        "Content-Type": "application/json"
    }

    try:
        resp = requests.post(
            f"{DESK_API}/content",
            headers=headers,
            json=payload,
            timeout=30
        )

        if resp.status_code == 201:
            if verbose:
                result = resp.json()
                print(f"  {C.DIM}Created: {result.get('id', '?')}{C.RESET}")
            return True
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

def filter_entries(entries, args):
    """Apply common filters (--filter, --skip-sections, config skip) to entry list."""
    if args.filter:
        entries = [(eid, pid) for eid, pid in entries if args.filter in eid]

    if args.skip_sections:
        before = len(entries)
        entries = [(eid, pid) for eid, pid in entries if not eid.startswith("section")]
        skipped = before - len(entries)
        if skipped:
            print(f"{C.DIM}Skipped {skipped} section entries{C.RESET}")

    if CONFIG_EXTERNAL_IDS:
        before = len(entries)
        entries = [(eid, pid) for eid, pid in entries if eid not in CONFIG_EXTERNAL_IDS]
        skipped_cfg = before - len(entries)
        if skipped_cfg:
            print(f"{C.DIM}Skipped {skipped_cfg} config entries (served by ConfigurationService){C.RESET}")

    # Skip external IDs known to cause issues on the reference server.
    # Maintained in scripts/migrate-skip.txt (one external ID per line).
    skip_ids = load_skip_ids()
    if skip_ids:
        before = len(entries)
        entries = [(eid, pid) for eid, pid in entries if eid not in skip_ids]
        skipped_known = before - len(entries)
        if skipped_known:
            print(f"{C.DIM}Skipped {skipped_known} known-problematic entries (see migrate-skip.txt){C.RESET}")

    return entries


def cmd_discover(args):
    """Discover what needs migrating."""
    print(f"{C.BOLD}Discovering legacy content...{C.RESET}")
    print()

    desk_token = login(DESK_API)

    entries = load_external_ids()
    if not entries:
        return

    entries = filter_entries(entries, args)
    print(f"Found {len(entries)} external IDs to check")
    print()

    needs_migration = []
    already_available = []

    for i, (ext_id, policy_id) in enumerate(entries):
        if (i + 1) % 50 == 0:
            print(f"{C.DIM}  Checked {i + 1}/{len(entries)}...{C.RESET}")

        if desk_already_has(desk_token, ext_id):
            already_available.append(ext_id)
        else:
            needs_migration.append((ext_id, policy_id))

    print()
    print(f"{C.BOLD}Results:{C.RESET}")
    print(f"  {C.OK}Already available on desk-api: {len(already_available)}{C.RESET}")
    print(f"  {C.WARN}Needs migration: {len(needs_migration)}{C.RESET}")

    if needs_migration and args.verbose:
        print()
        print(f"{C.BOLD}Items needing migration:{C.RESET}")
        prefixes = {}
        for ext_id, policy_id in needs_migration:
            prefix = ext_id.split(".")[0] if "." in ext_id else ext_id.split(":")[0]
            prefixes.setdefault(prefix, []).append(ext_id)

        for prefix in sorted(prefixes.keys()):
            items = prefixes[prefix]
            print(f"  {prefix}: {len(items)}")
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

    entries = load_external_ids()
    if not entries:
        return

    entries = filter_entries(entries, args)
    print(f"Processing {len(entries)} external IDs...")
    print()

    migrated = 0
    skipped = 0
    failed = 0
    not_found = 0
    not_found_ids = []
    failed_ids = []

    for i, (ext_id, policy_id) in enumerate(entries):
        # Progress
        if (i + 1) % 25 == 0 or i == 0:
            print(f"{C.DIM}[{i + 1}/{len(entries)}] "
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
            failed_ids.append((ext_id, f"FETCH: {e}"))
            continue

        if resp.status_code != 200:
            if args.verbose:
                print(f"  {C.DIM}NOT FOUND on reference: {ext_id} ({resp.status_code}){C.RESET}")
            not_found += 1
            not_found_ids.append(ext_id)
            continue

        content_json = resp.json()

        if args.verbose:
            aspect_names = list(content_json.get("aspects", {}).keys())
            print(f"  {C.INFO}MIGRATE {ext_id} -> {policy_id or '?'} "
                  f"({len(aspect_names)} aspects){C.RESET}")

        if migrate_one(desk_token, ext_id, content_json, policy_id,
                       dry_run=args.dry_run, verbose=args.verbose):
            migrated += 1
        else:
            failed += 1
            failed_ids.append((ext_id, "migrate_one returned False"))

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

    # Write report file
    report_path = os.path.join(os.path.dirname(__file__), "migrate-report.txt")
    with open(report_path, "w") as f:
        f.write(f"# Migration report - {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"# Migrated: {migrated}, Skipped: {skipped}, "
                f"Not found: {not_found}, Failed: {failed}\n\n")
        if failed_ids:
            f.write("## FAILED (need investigation)\n")
            for eid, reason in failed_ids:
                f.write(f"{eid} | {reason}\n")
            f.write("\n")
        if not_found_ids:
            f.write("## NOT FOUND on reference server\n")
            for eid in not_found_ids:
                f.write(f"{eid}\n")
    print(f"\n  Report written to: {report_path}")


def cmd_verify(args):
    """Verify migrated content is accessible."""
    print(f"{C.BOLD}Verifying migration...{C.RESET}")
    print()

    desk_token = login(DESK_API)

    entries = load_external_ids()
    if not entries:
        return

    entries = filter_entries(entries, args)

    ok = 0
    fail = 0

    for i, (ext_id, policy_id) in enumerate(entries):
        if (i + 1) % 50 == 0:
            print(f"{C.DIM}  Verified {i + 1}/{len(entries)}...{C.RESET}")

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
    global DESK_API, REFERENCE

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
