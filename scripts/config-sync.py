#!/usr/bin/env python3
"""
Config Sync Script for desk-api

Reads config-mapping.txt and extracts/copies config files from the original
Polopoly/Gong/ADM source codebases into desk-api's resource config directories.

Extraction types:
  xml-cdata      - Extract JSON from <component name="value" group="data"> CDATA in XML
  xml-component  - Build JSON from <component group="X" name="value">Y</component> elements
  json-ref       - XML references a separate JSON/JSON5 file; find and copy it
  json           - Direct JSON/JSON5 file copy
  manual         - Skip (manually maintained)

Usage:
  python config-sync.py                    # Dry run (show what would change)
  python config-sync.py --apply            # Actually write files
  python config-sync.py --check            # Exit 1 if any files are out of sync
  python config-sync.py --verbose          # Show details for each file
"""

import argparse
import json
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
    "gong-copyfit": PROJECT_ROOT.parent / "gong" / "content" / "copyfit-content" / "src" / "main" / "content",
    "gong-onecms-common": PROJECT_ROOT.parent / "gong" / "onecms-common" / "content" / "src" / "main" / "content",
}

# XML namespace used by Polopoly batch files
NS = {"p": "http://www.polopoly.com/polopoly/cm/xmlio"}

# Map config subdirectory to UI group name
SUBDIR_TO_GROUP = {
    "audioai": "Integrations",
    "ayrshare": "Integrations",
    "camel-routes": "Ingestion",
    "cfg": "System",
    "chains-list": "Ingestion",
    "desk-configuration": "UI",
    "desk-configuration/form": "UI",
    "desk-configuration/permission": "Permissions",
    "desk-configuration/result-set": "UI",
    "desk-system-configuration": "System",
    "distributionlist": "Publishing",
    "editor-configuration": "Editor",
    "editor-configuration/permission": "Permissions",
    "export": "Publishing",
    "form-templates": "Templates",
    "gallery-configurations": "UI",
    "image-service": "System",
    "importer": "Ingestion",
    "index": "Search",
    "layout": "Publishing",
    "localization/desk": "Locale",
    "localization/locale": "Locale",
    "mail-service": "Integrations",
    "markas": "UI",
    "partition": "System",
    "permissions": "Permissions",
    "processors-list": "Ingestion",
    "publish": "Publishing",
    "remotes": "Publishing",
    "reset-password": "System",
    "restrictContent": "Permissions",
    "routes-list": "Ingestion",
    "sendcontent": "Publishing",
    "settings": "System",
    "smartupdates": "UI",
    "tag-manager": "Integrations",
    "widgets-configuration": "Editor",
    "workflow": "Workflow",
}


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


def extract_content_name(xml_path: Path, external_id: str) -> str | None:
    """
    Extract the display name from an XML file's <component group="polopoly.Content" name="name"> element.
    Returns None if no name is found.
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError:
        return None

    content_blocks = root.findall(".//p:content", NS)
    if not content_blocks:
        content_blocks = root.findall(".//content")
    if not content_blocks:
        content_blocks = [root]

    for content in content_blocks:
        # Check if this content block matches our external ID
        ext_id_elem = content.find(".//p:metadata/p:contentid/p:externalid", NS)
        if ext_id_elem is None:
            ext_id_elem = content.find(".//metadata/contentid/externalid")
        if ext_id_elem is not None and ext_id_elem.text:
            if ext_id_elem.text.strip() != external_id:
                continue

        # Look for the name component
        for comp in content.findall(".//p:component", NS) + content.findall(".//component"):
            if comp.get("group") == "polopoly.Content" and comp.get("name") == "name" and comp.text:
                return comp.text.strip()

    # Fallback: single content block, try any polopoly.Content/name component
    if len(content_blocks) == 1:
        for comp in content_blocks[0].findall(".//p:component", NS) + content_blocks[0].findall(".//component"):
            if comp.get("group") == "polopoly.Content" and comp.get("name") == "name" and comp.text:
                return comp.text.strip()

    return None


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


def extract_xml_components(xml_path: Path, external_id: str) -> str | None:
    """
    Build a JSON object from <component group="X" name="value">Y</component> elements.

    Used for Polopoly configs where each component group is a field name and the text
    content of the <component name="value"> child is the field value.
    Skips group="polopoly.Content" (metadata) and contentref elements.
    """
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
    except ET.ParseError as e:
        print(f"  ERROR: Failed to parse XML {xml_path}: {e}", file=sys.stderr)
        return None

    content_blocks = root.findall(".//p:content", NS)
    if not content_blocks:
        content_blocks = root.findall(".//content")
    if not content_blocks:
        content_blocks = [root]

    for content in content_blocks:
        # Match by external ID
        ext_id_elem = content.find(".//p:metadata/p:contentid/p:externalid", NS)
        if ext_id_elem is None:
            ext_id_elem = content.find(".//metadata/contentid/externalid")
        if ext_id_elem is not None and ext_id_elem.text:
            if ext_id_elem.text.strip() != external_id:
                continue

        # Collect component values: group → text content
        fields = {}
        for comp in content.findall(".//p:component", NS) + content.findall(".//component"):
            group = comp.get("group", "")
            name = comp.get("name", "")
            if group == "polopoly.Content":
                continue  # Skip metadata components
            if name == "value" and comp.text:
                fields[group] = comp.text.strip()

        if fields:
            return json.dumps(fields, indent=2)

    print(f"  WARNING: No component data found for '{external_id}' in {xml_path}", file=sys.stderr)
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


def fix_json_escape_sequences(content: str) -> str:
    """
    Fix invalid JSON escape sequences that are common in Camel route configs.

    JSON only allows these escape sequences: quote, backslash, slash, b, f, n, r, t, uXXXX.
    Regex patterns like \\.jpg or \\S+ have invalid escapes that Gson rejects.

    This function escapes backslashes that are followed by characters that
    are NOT valid JSON escape characters, making the content valid JSON.
    """
    result = []
    in_string = False
    i = 0
    while i < len(content):
        char = content[i]

        if char == '"' and (i == 0 or content[i-1] != '\\'):
            in_string = not in_string
            result.append(char)
            i += 1
        elif char == '\\' and in_string and i + 1 < len(content):
            next_char = content[i + 1]
            # Valid JSON escape characters
            if next_char in '"\\bfnrtu/':
                # Emit both chars and skip past the pair
                result.append(char)
                result.append(next_char)
                i += 2
            elif next_char in '\r\n':
                # JSON5 line continuation (backslash before newline) — leave as-is
                result.append(char)
                i += 1
            else:
                # Invalid escape - add extra backslash to escape this one
                # Input: \.  Output: \\.
                result.append('\\')
                result.append('\\')
                i += 1
        else:
            result.append(char)
            i += 1

    return ''.join(result)


def inject_meta(content: str, name: str | None, external_id: str, group: str | None = None, fmt: str | None = None) -> str:
    """
    Inject a _meta block at the top of a JSON/JSON5 object.

    If name is None, uses the external_id as the display name.
    Always inserts on a new line with 2-space indentation after the opening '{'.
    """
    display_name = name if name else external_id
    # Escape quotes in the name for JSON
    display_name = display_name.replace('\\', '\\\\').replace('"', '\\"')

    meta_parts = [f'"name": "{display_name}"']
    if group:
        escaped_group = group.replace('\\', '\\\\').replace('"', '\\"')
        meta_parts.append(f'"group": "{escaped_group}"')
    if fmt:
        escaped_fmt = fmt.replace('\\', '\\\\').replace('"', '\\"')
        meta_parts.append(f'"format": "{escaped_fmt}"')
    meta_line = '"_meta": {' + ', '.join(meta_parts) + '},'

    # Find the first '{' and insert _meta right after it
    idx = content.find('{')
    if idx == -1:
        return content  # Not a JSON object — leave unchanged

    # Skip any whitespace/newline after the opening brace
    rest = content[idx + 1:]
    stripped = rest.lstrip()

    return content[:idx + 1] + "\n  " + meta_line + "\n  " + stripped


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
    content_name = None

    # Extract content based on type
    content = None

    if ext_type == "xml-cdata":
        if not source_file.exists():
            return "error", f"Source XML not found: {source_file}"
        content = extract_xml_cdata(source_file, external_id)
        content_name = extract_content_name(source_file, external_id)

    elif ext_type == "xml-component":
        if not source_file.exists():
            return "error", f"Source XML not found: {source_file}"
        content = extract_xml_components(source_file, external_id)
        content_name = extract_content_name(source_file, external_id)

    elif ext_type == "json-ref":
        # Support "xmlfile.xml + path/to/file.json" syntax: split on " + "
        # and use the second part as a direct JSON file path relative to source_dir
        if " + " in source_path:
            xml_part, json_part = source_path.split(" + ", 1)
            xml_file = source_dir / xml_part.strip()
            json_direct = source_dir / json_part.strip()
            if json_direct.exists():
                content = json_direct.read_text(encoding="utf-8")
            else:
                return "error", f"Source JSON not found: {json_direct}"
            # Extract name from the XML part
            if xml_file.exists():
                content_name = extract_content_name(xml_file, external_id)
        elif not source_file.exists():
            return "error", f"Source XML not found: {source_file}"
        else:
            json_path = find_json_ref(source_file, external_id)
            if json_path:
                content = json_path.read_text(encoding="utf-8")
            content_name = extract_content_name(source_file, external_id)

    elif ext_type == "json":
        json_path = find_json_file(source_dir, source_path, external_id)
        if json_path:
            content = json_path.read_text(encoding="utf-8")

    else:
        return "error", f"Unknown type: {ext_type}"

    if content is None:
        return "error", f"Failed to extract content for {external_id}"

    # Fix invalid JSON escape sequences (e.g., \.jpg in Camel route configs)
    content = fix_json_escape_sequences(content)

    # Inject _meta block with display name, group, and format
    group = SUBDIR_TO_GROUP.get(dest_subdir)
    # Template configs use input-template name as format (matches Polopoly policy-based serialization)
    fmt = "atex.onecms.Template.it" if dest_subdir == "form-templates" else None
    content = inject_meta(content, content_name, external_id, group, fmt)

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
