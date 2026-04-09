# Append/Sum Content Engine — Test Plan

**Ticket:** AMS-17152 / PMC-2255
**Commit:** `7b554f04db4c65d89f319ca44b0532c66de9e9de`
**Target:** http://localhost:38084/desk/#/

---

## Scope

This plan covers the Append/Sum content engine merged from the Icelandic Workflows (Obits/Listings) branch. The feature adds:

- Client-side append engine (`AppendUtils` in `onecms-widgets`) that loads, transforms, and saves content in the browser
- Server-side REST endpoint `POST /onecms/dam/append/content/` (alternative backend path)
- Dot-notation expression parser for field selectors
- UI toolbar buttons: Append Content, Append Content Dialog, Append to Pinned, Remove Appended Content, Sum Content
- Engagement tracking (bi-directional "Appended to" / "Appended from" relationships)
- Print layout summation (column depths, frame depths, word counts, distinct status counts)
- Locale support for all new UI strings (en, de, dk, es, it, nl, no, sv, tr, vi)

---

## Prerequisites

Before testing, verify the following configuration content is imported into the database:

| External ID | Purpose | Verify |
|---|---|---|
| `atex.configuration.desk.append-command` | Append profiles and commands | `curl -L /onecms/content/externalid/atex.configuration.desk.append-command` |
| `atex.configuration.desk.sum-command` | Sum operation definitions | `curl -L /onecms/content/externalid/atex.configuration.desk.sum-command` |
| `atex.configuration.desk.configurations` | Must include `"append-command"` and `"sum-command"` keys in Default config | Check JSON for both keys |
| `atex.onecms.preload-deskList` | Must include `"AppendUtils"` in services array | Check JSON services list |
| `atex.onecms.factory-AppendUtils` | The client-side append engine (from `onecms-widgets`) | Verify content exists |

**Quick validation:**
```bash
TOKEN=$(curl -s -X POST http://localhost:38084/onecms/security/token \
  -H 'Content-Type: application/json' \
  -d '{"username":"sysadmin","password":"sysadmin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# Check append-command config exists
curl -s -L -H "X-Auth-Token: $TOKEN" \
  http://localhost:38084/onecms/content/externalid/atex.configuration.desk.append-command | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if d.get('id') else 'MISSING')"

# Check configurations includes append-command and sum-command
curl -s -L -H "X-Auth-Token: $TOKEN" \
  http://localhost:38084/onecms/content/externalid/atex.configuration.desk.configurations | python3 -c "
import sys,json; d=json.load(sys.stdin)
v=d['aspects']['contentData']['data'].get('json','') or d['aspects']['contentData']['data'].get('dataValue','')
print('append-command:', 'append-command' in v, 'sum-command:', 'sum-command' in v)"

# Check AppendUtils is in preload list
curl -s -L -H "X-Auth-Token: $TOKEN" \
  http://localhost:38084/onecms/content/externalid/atex.onecms.preload-deskList | python3 -c "
import sys,json; d=json.load(sys.stdin)
v=d['aspects']['contentData']['data'].get('json','') or d['aspects']['contentData']['data'].get('dataValue','')
print('AppendUtils in preload:', 'AppendUtils' in v)"

# Check AppendUtils factory exists
curl -s -L -H "X-Auth-Token: $TOKEN" \
  http://localhost:38084/onecms/content/externalid/atex.onecms.factory-AppendUtils | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if d.get('id') else 'MISSING')"
```

If any config is missing, the UI will fail silently or throw errors. See "Known Issues" section below.

---

## Test Data

The script `append-test.py` creates articles suitable for testing:

| Content | Priority | Words | Purpose |
|---------|----------|-------|---------|
| Target Article | 1 | 12 | Destination for appends (identified by `priority=targetValue`) |
| Source Article 1 | 2 | 45 | Icelandic obit — short body |
| Source Article 2 | 3 | 62 | Icelandic obit — medium body |
| Source Article 3 | 4 | 78 | Icelandic obit — longer body |

All articles are prefixed with `APPEND-TEST` for easy identification in Desk search.

**Important:** The target article must have `priority` matching the profile's `targetValue` (default: `1`). The `priority` field must be present in the content data returned by the list API.

```bash
# Seed fresh test content (required before each UI test run)
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --seed-only

# Seed and run full API test suite
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --verbose
```

**Note:** Always seed fresh content before UI testing. Content that has already been appended will be skipped by `AppendUtils` (it checks for existing "Appended to" engagements), resulting in "0 items processed".

---

## API Tests (automated via append-test.py)

These test the server-side REST endpoint directly. They do not exercise the `AppendUtils` client-side engine.

```bash
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --verbose
```

| # | Test | What it verifies |
|---|------|-----------------|
| 1 | `test_login` | Authentication via `POST /security/token` |
| 2 | `test_seed_content` | Article creation with priority, words, body, byline fields |
| 3 | `test_basic_append` | `POST /dam/append/content/` with sort-by-priority config |
| 4 | `test_append_with_commands` | Append with `appendNote` + `appendElement` commands, engagement creation |
| 5 | `test_append_test_mode` | `testMode=true` returns empty processedIds (no persistence) |
| 6 | `test_append_missing_target` | Missing targetContentId returns error message, not HTTP 500 |
| 7 | `test_append_empty_entries` | Empty entries returns `__WS_APPEND_CONTENT_ERROR_NO_SOURCE_CONTENT__` |
| 8 | `test_remove_appended_content` | Remove operation with `operation: "remove"` |
| 9 | `test_verify_target_content` | GET target article, verify body was modified |

---

## UI Tests (manual — Desk at http://localhost:38084/desk/#/)

### Setup for each UI test session

1. Seed fresh content: `python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --seed-only`
2. Hard refresh Desk (Ctrl+Shift+R) to clear cached config
3. Clear localStorage if append profiles were previously saved (or use incognito)
4. Search for `APPEND-TEST` to find the test articles

### TC-UI-01: Append toolbar button visibility

**Precondition:** Append config loaded, 4 test articles visible

1. Select 0 articles — verify "Append Content" and "Sum Content" buttons are NOT visible
2. Select 1 article — verify "Sum Content" appears, "Append Content" does NOT appear
3. Select all 4 test articles (including target with priority=1) — verify "Append Content", "Append Content Dialog", and "Sum Content" all appear
4. Deselect all — buttons disappear

**What this tests:** `canAppendContent()` correctly evaluates selection against profile's `targetValue` and `sortField`

### TC-UI-02: Append content via direct button

**Precondition:** All 4 FRESH test articles selected (never previously appended)

1. Click "Append Content" button
2. Verify success notification: "Append content successful: N items processed"
3. Open the target article (priority=1) in the editor
4. Verify body contains appended content from source articles
5. Verify appended content is wrapped with `x-atex-append-content` attributes
6. Verify engagement list shows "Appended from" relationships on target
7. Open a source article — verify "Appended to" engagement exists

### TC-UI-03: Append content dialog

**Precondition:** Fresh test articles (seed again if needed)

1. Select all 4 test articles
2. Click "Append Content Dialog..." button
3. Verify modal opens with profile dropdown
4. Verify profiles listed: "Append body and name", "Append Body, Author and images"
5. Verify profile descriptions and command table (From/To columns) display correctly
6. Select "Append body and name" profile, click OK
7. Verify success notification appears
8. Open target article — verify body and name were appended

### TC-UI-04: Append already-appended content

**Precondition:** Content from TC-UI-02 or TC-UI-03 (already appended)

1. Select the same 4 articles
2. Click "Append Content"
3. Verify notification: "Append content successful: 0 items processed" (not a hang or silent failure)

**What this tests:** `AppendUtils.validSourceEntries` filters already-engaged content, and `doAppendContent` resolves the promise when `response.processed` is false

### TC-UI-05: Append to pinned content

**Precondition:** Fresh test articles

1. Pin (anchor) the target article in the list view
2. Select 2+ source articles (do NOT select the target)
3. Verify "Append to Pinned" button appears
4. Click it — verify content is appended to the pinned (anchored) article
5. Verify success notification

### TC-UI-06: Remove appended content

**Precondition:** Content that was previously appended (from TC-UI-02)

1. Select articles that have engagement relationships
2. Verify "Remove Appended Content" button appears
3. Click it
4. Open target article — verify appended content and engagements are removed
5. Open source articles — verify "Appended to" engagements are removed

### TC-UI-07: Sum content — word count

**Precondition:** Test articles selected

1. Select 3+ articles with word counts
2. Click "Sum Content" button
3. Verify modal opens with metric dropdown
4. Select "Sum Words" metric
5. Verify sum displays (expected: 45 + 62 + 78 = 185 for 3 source articles, or 197 including target)
6. Verify units label shows "Words"

### TC-UI-08: Sum content — distinct status

1. Select articles with different status values
2. Open sum modal
3. Select "Count Distinct Status" metric
4. Verify status values listed with counts, e.g. "draft(3)"

### TC-UI-09: Sum content — print depth

**Precondition:** Articles with print layout data (may require copyfit-enabled articles)

1. Select articles with print layout data
2. Open sum modal
3. Select "Total Print Depth" or "Body Print Depth" metric
4. Verify depth values summed and converted to configured units (cm)
5. Select "Body Frame Depth" — verify frame depth calculation (uses `sumPrintFrames`)

### TC-UI-10: Sum content — metric switching

1. Open sum modal with articles selected
2. Switch between different metrics (Words, Status, Print Depth)
3. Verify sum recalculates on each switch
4. Select "- Clear Selection -" — verify sum clears
5. Click Cancel — verify no config change persisted

### TC-UI-11: Notification feedback when conditions not met

**Precondition:** Append config loaded

1. Select only 1 article — click "Append Content" (if visible)
2. Verify warning notification explaining why append cannot proceed: article count and/or missing target
3. Select 3 articles but NONE with priority=1
4. Click "Append Content" — verify warning mentions "No target article found (priority=1)"

### TC-UI-12: Locale verification

1. Switch Desk language to German (or another non-English locale)
2. Perform an append operation
3. Verify success/failure notifications appear in the selected language
4. Open sum modal — verify "Measure selected text..." header is translated
5. Trigger a "nothing to do" append — verify warning is translated

---

## Error Handling Tests

| # | Scenario | Expected | How to test |
|---|----------|----------|-------------|
| E-01 | POST append with null config | Error in response, not HTTP 500 | `curl` with `"config": null` |
| E-02 | POST append with invalid content ID | Error message in response | `curl` with fake UUID |
| E-03 | POST append with non-article content | Graceful skip or error | Select images in Desk, try append |
| E-04 | Append config not imported | Toolbar buttons hidden, console error on init | Remove `append-command` from configurations |
| E-05 | Sum with no print layout data | Sum returns 0, no crash | Select articles without print data, sum print depth |
| E-06 | AppendUtils not in preload | `$injector.get("AppendUtils")` fails, error caught | Remove from preload-deskList |
| E-07 | localStorage has stale profile object | `getProfile()` handles object from localStorage correctly | Save profile, reload page, verify append still works |
| E-08 | Malformed JSON to REST endpoint | HTTP 400 or error, not 500 | `curl` with invalid JSON body |

---

## Regression Checks

Fixes from the CoPilot review applied in the commit:

| # | Fix | How to verify |
|---|-----|--------------|
| R-01 | NPE in AppendContentResource on parse error | Send malformed JSON to `/dam/append/content/` — expect error, not 500 |
| R-02 | Broken setter lookup in Reflector | Append with dot-notation selector (use "Append Body, Author and images" profile) |
| R-03 | JUnit Assert removed from production code | `grep -rn 'import.*junit.*Assert' module-desk/src/main/` should return nothing |
| R-04 | `sumPrintFrames` function name mismatch | Sum modal "Body Frame Depth" works without TypeError |
| R-05 | Double batch counter in print-layouts-service.js | Sum print layouts returns correct totals (not doubled) |
| R-06 | Missing early return in append-content-service.js | Append with missing profile shows notification, doesn't hang |

---

## Bugs Found During Testing

| # | Component | Description | Fix |
|---|-----------|-------------|-----|
| B-01 | `common-list-controller.js` | `canAppendContent` and `canSumContent` functions missing from master | Cherry-picked from RELENG-2-28-NW |
| B-02 | `sum-content-modal.js` | `sumPrintFrame` function name didn't match config's `sumPrintFrames` | Renamed to `sumPrintFrames` |
| B-03 | `atex.configuration.desk.configurations` | `append-command` and `sum-command` keys missing from Default config | Added to gong/desk and adm-mpp |
| B-04 | `AppendContentResult.java` / `AppendUtils.java` | `System.out.println` debug output in production code | Removed / moved to `logger.fine` |
| B-05 | `append-content-service.js` | `getProfile()` failed when localStorage contained profile object instead of name | Fixed to handle both object and string from localStorage |
| B-06 | `append-content-service.js` | No user feedback when append conditions not met | Added warning notification with locale-translated reasons |
| B-07 | `AppendUtils` (onecms-widgets) | `doAppendContent` deferred never resolved when `response.processed` is false | Added else branch to resolve deferred |
| B-08 | Locale files | All append/sum locale keys had English-only values | Translated to de, dk, es, it, nl, no, sv, tr, vi |

---

## Architecture Notes

The append feature has two code paths:

1. **Client-side (AppendUtils)** — Used by the Desk UI. Loads content via `ContentService.getContent()`, manipulates DOM in the browser (body text, engagements, print variants), saves via `ContentService.updateContent()`. Supports linked print content via `PrintService`. This is the primary path used in production.

2. **Server-side (AppendContentResource)** — REST endpoint at `POST /onecms/dam/append/content/`. Processes append operations on the server using `AppendContentDuplicator`. Used by the API test suite and potentially by external integrations.

The two paths share configuration (`append-command` profiles) but have independent implementations. The client-side path requires `PrintStyles` and `PrintUtils` services, which are loaded via the onecms-widgets preload framework.

### Key configuration flow

```
atex.configuration.desk.configurations (Default config)
  └── "append-command": "default-append-command"
        └── atex.configuration.desk.append-command (profiles + commands)
  └── "sum-command": "default-sum-command"
        └── atex.configuration.desk.sum-command (sum operations)

atex.onecms.preload-deskList (widget preload)
  └── "AppendUtils" (services array)
        └── atex.onecms.factory-AppendUtils (client-side engine)
```

### Target article identification

The append operation identifies the target article by matching `contentData[sortField] === targetValue` across selected articles. Default config: `sortField: "priority"`, `targetValue: 1`. Articles must have the `priority` field in their content data. The remaining selected articles become sources, sorted by `priority` ascending.

---

## Running

```bash
# Seed fresh test content
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --seed-only

# Full API test suite
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --verbose

# Single API test
python3 scripts/append-test.py --ref-url http://localhost:38084/onecms --test basic_append --verbose

# Against a different server
python3 scripts/append-test.py --ref-url http://some-host:38084/onecms
```
