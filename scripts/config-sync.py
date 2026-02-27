#!/usr/bin/env python3
"""
Config Sync Script for desk-api

Reads config-mapping.txt and extracts/copies config files from the original
Polopoly/Gong/ADM source codebases into desk-api's resource config directories.

Extraction types:
  xml-cdata  - Extract JSON from <component name="value" group="data"> CDATA in XML
  json-ref   - XML references a separate JSON/JSON5 file; find and copy it
  json       - Direct JSON/JSON5 file copy
  manual     - Skip (manually maintained)

Usage:
  python config-sync.py                    # Dry run (show what would change)
  python config-sync.py --apply            # Actually write files
  python config-sync.py --check            # Exit 1 if any files are out of sync
  python config-sync.py --verbose          # Show details for each file
"""

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Project root (one level up from scripts/)
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent
MAPPING_FILE = SCRIPT_DIR / "config-mapping.txt"
DEST_BASE = PROJECT_ROOT / "src" / "main" / "resources" / "config"

# Source base directories (relative to project root's parent)
SOURCE_BASES = {
    "gong-desk": PROJECT_ROOT.parent / "gong" / "desk" / "content" / "src" / "main" / "content",
    "gong-common": PROJECT_ROOT.parent / "gong" / "content" / "common" / "src" / "main" / "content",
    "adm-starterkit": PROJECT_ROOT.parent / "adm-starterkit" / "content" / "custom-content" / "src" / "main" / "content",
    "adm-starterkit-pseries": PROJECT_ROOT.parent / "adm-starterkit" / "content" / "custom-pseries-content" / "src" / "main" / "content",
}

# XML namespace used by Polopoly batch files
NS = {"p": "http://www.polopoly.com/polopoly/cm/xmlio"}


def parse_mapping(mapping_file: Path) -> list[dict]:
    """Parse the config-mapping.txt file into a list of mapping entries."""
    entries = []
    with open(mapping_file, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split("|")
            if len(parts) != 6:
                print(f"WARNING: Line {line_num}: expected 6 fields, got {len(parts)}: {line}", file=sys.stderr)
                continue
            entries.append({
                "type": parts[0].strip(),
                "source_base": parts[1].strip(),
                "source_path": parts[2].strip(),
                "dest_tier": parts[3].strip(),
                "dest_subdir": parts[4].strip(),
                "external_id": parts[5].strip(),
                "line_num": line_num,
            })
    return entries


def extract_xml_cdata(xml_path: Path, external_id: str) -> str | None:
    """
    Extract JSON from an XML file's <component name="value" group="data"> element.

    Handles both namespaced and non-namespaced XML. The external_id is used to
    find the correct <content> block when the XML contains multiple entries.
    """
    try:
        # Parse with namespace awareness
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"  ERROR: Failed to parse XML {xml_path}: {e}", file=sys.stderr)
        return None

    # Try both namespaced and non-namespaced element lookups
    content_blocks = root.findall(".//p:content", NS)
    if not content_blocks:
        content_blocks = root.findall(".//content")
    if not content_blocks:
        # Maybe the root IS the content element
        content_blocks = [root]

    # Known component patterns that contain JSON data (group, name)
    JSON_COMPONENTS = [
        ("data", "value"),              # desk-configuration style
        ("contentData", "contentData"),  # DAM config style
        ("model", "pojo"),              # index/model config style
    ]

    for content in content_blocks:
        # Check if this content block matches our external ID
        ext_id_elem = content.find(".//p:metadata/p:contentid/p:externalid", NS)
        if ext_id_elem is None:
            ext_id_elem = content.find(".//metadata/contentid/externalid")
        if ext_id_elem is not None and ext_id_elem.text:
            found_id = ext_id_elem.text.strip()
            if found_id != external_id:
                continue

        # Look for component containing JSON data
        for comp in content.findall(".//p:component", NS) + content.findall(".//component"):
            comp_name = comp.get("name", "")
            comp_group = comp.get("group", "")
            if (comp_group, comp_name) in JSON_COMPONENTS and comp.text:
                json_text = comp.text.strip()
                # Strip CDATA wrapper if present (ET already handles this, but just in case)
                if json_text.startswith("<![CDATA["):
                    json_text = json_text[9:]
                if json_text.endswith("]]>"):
                    json_text = json_text[:-3]
                return json_text.strip()

    # Fallback: if only one content block and no ID match, try any component with JSON-like text
    if len(content_blocks) == 1:
        content = content_blocks[0]
        for comp in content.findall(".//p:component", NS) + content.findall(".//component"):
            text = (comp.text or "").strip()
            if text.startswith("{") or text.startswith("["):
                return text

    print(f"  WARNING: No JSON data found for external ID '{external_id}' in {xml_path}", file=sys.stderr)
    return None


def find_json_ref(xml_path: Path, external_id: str) -> Path | None:
    """
    Find the JSON file referenced by an XML template/locale file.

    Looks for:
    1. file="path/to/file.json5" attribute on <component> elements (locale style)
    2. <file name="...json" encoding="relative">path/to/file.json</file> (template style)
    3. file="path/to/file.json" attribute on <file> elements
    4. Companion JSON/JSON5 file in common relative locations (json/, files/, locales/)
    """
    xml_dir = xml_path.parent

    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError:
        pass
    else:
        # First pass: look in the correct <content> block matching our external ID
        content_blocks = root.findall(".//p:content", NS)
        if not content_blocks:
            content_blocks = root.findall(".//content")
        for content in content_blocks:
            ext_id_elem = content.find(".//p:metadata/p:contentid/p:externalid", NS)
            if ext_id_elem is None:
                ext_id_elem = content.find(".//metadata/contentid/externalid")
            if ext_id_elem is not None and ext_id_elem.text:
                if ext_id_elem.text.strip() != external_id:
                    continue

            # Check component file= attribute (locale pattern: <component ... file="locales/locale-en.json5">)
            for comp in content.findall(".//p:component", NS) + content.findall(".//component"):
                file_attr = comp.get("file")
                if file_attr and (".json" in file_attr):
                    candidate = xml_dir / file_attr.strip()
                    if candidate.exists():
                        return candidate

            # Check <file> elements
            for file_elem in content.iter():
                tag = file_elem.tag.split("}")[-1] if "}" in file_elem.tag else file_elem.tag
                if tag == "file":
                    # Check text content
                    ref_path = file_elem.text
                    if ref_path and (".json" in ref_path):
                        candidate = xml_dir / ref_path.strip()
                        if candidate.exists():
                            return candidate
                    # Check file= attribute
                    file_attr = file_elem.get("file")
                    if file_attr and ".json" in file_attr:
                        candidate = xml_dir / file_attr.strip()
                        if candidate.exists():
                            return candidate

        # Second pass: search all elements regardless of content block
        for elem in root.iter():
            # Check file= on any element
            file_attr = elem.get("file")
            if file_attr and (".json" in file_attr):
                candidate = xml_dir / file_attr.strip()
                if candidate.exists():
                    return candidate
            # Check <file> text
            tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag
            if tag == "file" and elem.text and ".json" in elem.text:
                candidate = xml_dir / elem.text.strip()
                if candidate.exists():
                    return candidate

    # Try common companion file patterns
    base_name = xml_path.stem  # filename without .xml
    for pattern_dir in ["json", "files", "locales", "."]:
        for ext in [".json5", ".json"]:
            # Try exact match first
            candidate = xml_dir / pattern_dir / (base_name + ext)
            if candidate.exists():
                return candidate

            # Try with external_id as filename
            candidate = xml_dir / pattern_dir / (external_id + ext)
            if candidate.exists():
                return candidate

    # Try searching for any JSON file in subdirectories that matches
    for sub in xml_dir.rglob("*.json*"):
        if external_id in sub.stem or base_name in sub.stem:
            return sub

    print(f"  WARNING: No JSON ref found for '{external_id}' near {xml_path}", file=sys.stderr)
    return None


def find_json_file(source_dir: Path, source_path: str, external_id: str) -> Path | None:
    """Find a direct JSON file to copy."""
    # Try the source_path directly
    candidate = source_dir / source_path
    if candidate.exists():
        return candidate

    # Try with .json5 extension variant
    if candidate.suffix == ".json":
        alt = candidate.with_suffix(".json5")
        if alt.exists():
            return alt

    # Search for the file by external_id
    for ext in [".json5", ".json"]:
        for found in source_dir.rglob(external_id + ext):
            return found

    print(f"  WARNING: JSON file not found: {candidate}", file=sys.stderr)
    return None


def sync_entry(entry: dict, apply: bool, verbose: bool) -> tuple[str, str]:
    """
    Process a single mapping entry.

    Returns (status, message) where status is one of:
      'ok'       - File exists and matches
      'updated'  - File was written (or would be in dry-run)
      'created'  - New file was created (or would be in dry-run)
      'skipped'  - Manual entry or missing source
      'error'    - Failed to process
    """
    ext_type = entry["type"]
    source_base_name = entry["source_base"]
    source_path = entry["source_path"]
    dest_tier = entry["dest_tier"]
    dest_subdir = entry["dest_subdir"]
    external_id = entry["external_id"]

    # Destination file
    dest_dir = DEST_BASE / dest_tier / dest_subdir
    dest_file = dest_dir / (external_id + ".json5")

    if ext_type == "manual":
        if dest_file.exists():
            return "ok", f"[manual] {external_id}"
        return "skipped", f"[manual] {external_id} (no source)"

    # Resolve source base directory
    source_dir = SOURCE_BASES.get(source_base_name)
    if source_dir is None:
        return "error", f"Unknown source_base: {source_base_name}"
    if not source_dir.exists():
        return "error", f"Source directory not found: {source_dir}"

    source_file = source_dir / source_path

    # Extract content based on type
    content = None

    if ext_type == "xml-cdata":
        if not source_file.exists():
            return "error", f"Source XML not found: {source_file}"
        content = extract_xml_cdata(source_file, external_id)

    elif ext_type == "json-ref":
        if not source_file.exists():
            return "error", f"Source XML not found: {source_file}"
        json_path = find_json_ref(source_file, external_id)
        if json_path:
            content = json_path.read_text(encoding="utf-8")

    elif ext_type == "json":
        json_path = find_json_file(source_dir, source_path, external_id)
        if json_path:
            content = json_path.read_text(encoding="utf-8")

    else:
        return "error", f"Unknown type: {ext_type}"

    if content is None:
        return "error", f"Failed to extract content for {external_id}"

    # Normalize line endings
    content = content.replace("\r\n", "\n")
    if not content.endswith("\n"):
        content += "\n"

    # Compare with existing file
    if dest_file.exists():
        existing = dest_file.read_text(encoding="utf-8").replace("\r\n", "\n")
        if existing == content:
            return "ok", f"{external_id}"
        status = "updated"
    else:
        status = "created"

    if apply:
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest_file.write_text(content, encoding="utf-8")

    return status, f"{external_id} -> {dest_tier}/{dest_subdir}/{external_id}.json5"


def main():
    parser = argparse.ArgumentParser(description="Sync config files from source codebases")
    parser.add_argument("--apply", action="store_true", help="Actually write files (default is dry-run)")
    parser.add_argument("--check", action="store_true", help="Exit 1 if any files are out of sync")
    parser.add_argument("--verbose", "-v", action="store_true", help="Show details for each file")
    parser.add_argument("--filter", type=str, help="Only process entries matching this external ID pattern")
    args = parser.parse_args()

    if not MAPPING_FILE.exists():
        print(f"ERROR: Mapping file not found: {MAPPING_FILE}", file=sys.stderr)
        sys.exit(1)

    entries = parse_mapping(MAPPING_FILE)
    print(f"Loaded {len(entries)} mapping entries from {MAPPING_FILE.name}")

    # Check source directories
    for name, path in SOURCE_BASES.items():
        status = "OK" if path.exists() else "MISSING"
        if args.verbose:
            print(f"  {name}: {path} [{status}]")

    counts = {"ok": 0, "updated": 0, "created": 0, "skipped": 0, "error": 0}

    for entry in entries:
        if args.filter and args.filter not in entry["external_id"]:
            continue

        status, message = sync_entry(entry, apply=args.apply, verbose=args.verbose)
        counts[status] += 1

        if args.verbose or status not in ("ok", "skipped"):
            prefix = {
                "ok": "  OK  ",
                "updated": "  UPD ",
                "created": "  NEW ",
                "skipped": "  SKIP",
                "error": "  ERR ",
            }[status]
            print(f"{prefix} {message}")

    # Summary
    print()
    mode = "APPLIED" if args.apply else "DRY RUN"
    print(f"[{mode}] {counts['ok']} unchanged, {counts['created']} new, "
          f"{counts['updated']} updated, {counts['skipped']} skipped, {counts['error']} errors")

    if args.check and (counts["updated"] > 0 or counts["created"] > 0):
        print("FAIL: Files are out of sync")
        sys.exit(1)

    if counts["error"] > 0:
        sys.exit(2)


if __name__ == "__main__":
    main()
