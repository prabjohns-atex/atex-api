#!/bin/bash
# Smoke test for desk-api — exercises key endpoints side by side.
# Usage: ./scripts/smoke-test.sh [base_url]
#   base_url defaults to http://localhost:8081

set -e

BASE=${1:-http://localhost:8081}
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; FAILURES=$((FAILURES+1)); }
info() { echo -e "${YELLOW}→ $1${NC}"; }

FAILURES=0

# ── 1. Auth ──
info "Logging in..."
TOKEN=$(curl -sf -X POST "$BASE/security/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"sysadmin","password":"sysadmin"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  fail "Login failed — cannot continue"
  exit 1
fi
pass "Login OK (token: ${TOKEN:0:20}...)"

AUTH="-H X-Auth-Token:$TOKEN"

# ── 2. Create Article ──
info "Creating article..."
ARTICLE_RESP=$(curl -sf -X POST "$BASE/content" \
  $AUTH -H "Content-Type: application/json" \
  -d '{
    "aspects": {
      "contentData": {
        "data": {
          "_type": "atex.onecms.article",
          "title": "Smoke Test Article",
          "headline": {"text": "Test Headline"},
          "lead": {"text": "Test lead paragraph"},
          "body": {"text": "<p>Body text for smoke test</p>"}
        }
      }
    }
  }')

ARTICLE_ID=$(echo "$ARTICLE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null)
ARTICLE_VER=$(echo "$ARTICLE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('version',''))" 2>/dev/null)

if [ -z "$ARTICLE_ID" ]; then
  fail "Create article failed"
  echo "$ARTICLE_RESP"
else
  pass "Create article: $ARTICLE_ID"
fi

# ── 3. Fetch Article ──
if [ -n "$ARTICLE_ID" ]; then
  info "Fetching article by ID..."
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/content/contentid/$ARTICLE_VER" $AUTH)
  if [ "$HTTP_CODE" = "200" ]; then
    pass "Fetch article: 200 OK"
  else
    fail "Fetch article: HTTP $HTTP_CODE"
  fi
fi

# ── 4. Upload Image File ──
info "Uploading test image file..."
# Create a tiny 1x1 PNG
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18\xd8N\x00\x00\x00\x00IEND\xaeB`\x82' > /tmp/smoke-test.png

UPLOAD_RESP=$(curl -sf -X POST "$BASE/file/tmp/anonymous/smoke-test.png" \
  $AUTH -H "Content-Type: image/png" \
  --data-binary @/tmp/smoke-test.png 2>/dev/null || echo "UPLOAD_FAILED")

if echo "$UPLOAD_RESP" | grep -q "UPLOAD_FAILED"; then
  fail "Upload image file"
else
  FILE_URI=$(echo "$UPLOAD_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('uri',''))" 2>/dev/null || echo "")
  if [ -n "$FILE_URI" ]; then
    pass "Upload image file: $FILE_URI"
  else
    pass "Upload image file (response: ${UPLOAD_RESP:0:80})"
    FILE_URI="tmp://anonymous/smoke-test.png"
  fi
fi

# ── 5. Create Image Content ──
info "Creating image content..."
IMAGE_RESP=$(curl -sf -X POST "$BASE/content" \
  $AUTH -H "Content-Type: application/json" \
  -d "{
    \"aspects\": {
      \"contentData\": {
        \"data\": {
          \"_type\": \"atex.onecms.image\",
          \"title\": \"Smoke Test Image\"
        }
      },
      \"atex.Image\": {
        \"data\": {
          \"_type\": \"com.atex.onecms.image.ImageInfoAspectBean\",
          \"filePath\": \"$FILE_URI\",
          \"width\": 1,
          \"height\": 1
        }
      },
      \"atex.Files\": {
        \"data\": {
          \"_type\": \"com.atex.onecms.content.FilesAspectBean\",
          \"files\": {
            \"smoke-test.png\": {
              \"_type\": \"com.atex.onecms.content.ContentFileInfo\",
              \"filePath\": \"smoke-test.png\",
              \"fileUri\": \"$FILE_URI\"
            }
          }
        }
      }
    }
  }")

IMAGE_ID=$(echo "$IMAGE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id',''))" 2>/dev/null)
IMAGE_VER=$(echo "$IMAGE_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('version',''))" 2>/dev/null)

if [ -z "$IMAGE_ID" ]; then
  fail "Create image content"
  echo "$IMAGE_RESP"
else
  pass "Create image content: $IMAGE_ID"
fi

# ── 6. Fetch Image Content ──
if [ -n "$IMAGE_ID" ]; then
  info "Fetching image content..."
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/content/contentid/$IMAGE_VER" $AUTH)
  if [ "$HTTP_CODE" = "200" ]; then
    pass "Fetch image content: 200 OK"
  else
    fail "Fetch image content: HTTP $HTTP_CODE"
  fi

  # Check that atex.Image filePath was committed from tmp:// to content://
  info "Checking file commit (tmp → content)..."
  IMAGE_DATA=$(curl -sf "$BASE/content/contentid/$IMAGE_VER" $AUTH)
  IMAGE_FP=$(echo "$IMAGE_DATA" | python3 -c "
import sys,json
d=json.load(sys.stdin)
img = d.get('aspects',{}).get('atex.Image',{}).get('data',{})
print(img.get('filePath',''))
" 2>/dev/null)
  if echo "$IMAGE_FP" | grep -q "^content://"; then
    pass "File committed: $IMAGE_FP"
  elif echo "$IMAGE_FP" | grep -q "^tmp://"; then
    fail "File NOT committed (still tmp://): $IMAGE_FP"
  else
    info "File path: $IMAGE_FP (may be OK if local storage)"
  fi
fi

# ── 7. Image Service Redirect ──
if [ -n "$IMAGE_ID" ]; then
  info "Testing image service redirect..."
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -L --max-redirs 0 "$BASE/image/$IMAGE_ID/photo.jpg?w=100" $AUTH)
  if [ "$HTTP_CODE" = "302" ]; then
    pass "Image service redirect: 302"
  elif [ "$HTTP_CODE" = "404" ]; then
    info "Image service: 404 (image service may not be enabled)"
  else
    fail "Image service: HTTP $HTTP_CODE"
  fi
fi

# ── 8. File Download ──
info "Testing file download..."
if [ -n "$IMAGE_FP" ] && [ "$IMAGE_FP" != "" ]; then
  # Convert URI scheme://host/path to /file/scheme/host/path
  FILE_PATH=$(echo "$IMAGE_FP" | sed 's|://|/|')
  HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/file/$FILE_PATH" $AUTH)
  if [ "$HTTP_CODE" = "200" ]; then
    pass "File download: 200 OK"
  else
    fail "File download ($FILE_PATH): HTTP $HTTP_CODE"
  fi
fi

# ── 9. Search ──
info "Testing search endpoint..."
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/search/public/select?q=*:*&rows=1" $AUTH)
if [ "$HTTP_CODE" = "200" ]; then
  pass "Search: 200 OK"
else
  fail "Search: HTTP $HTTP_CODE (Solr may not be running)"
fi

# ── 10. Activities (locking) ──
if [ -n "$ARTICLE_ID" ]; then
  info "Testing activity write..."
  ACT_CODE=$(curl -sf -o /dev/null -w "%{http_code}" -X PUT \
    "$BASE/activities/$ARTICLE_ID/sysadmin/atex.dm.desk" \
    $AUTH -H "Content-Type: application/json" \
    -d '{"activity":"editing","params":{}}')
  if [ "$ACT_CODE" = "200" ]; then
    pass "Activity write: 200 OK"
  else
    fail "Activity write: HTTP $ACT_CODE"
  fi

  info "Testing activity read..."
  ACT_READ=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/activities/$ARTICLE_ID" $AUTH)
  if [ "$ACT_READ" = "200" ]; then
    pass "Activity read: 200 OK"
  else
    fail "Activity read: HTTP $ACT_READ"
  fi
fi

# ── 11. Configuration ──
info "Testing configuration endpoint..."
HTTP_CODE=$(curl -sf -o /dev/null -w "%{http_code}" "$BASE/admin/config" $AUTH)
if [ "$HTTP_CODE" = "200" ]; then
  pass "Configuration: 200 OK"
else
  fail "Configuration: HTTP $HTTP_CODE"
fi

# ── Summary ──
echo ""
if [ $FAILURES -eq 0 ]; then
  echo -e "${GREEN}All tests passed!${NC}"
else
  echo -e "${RED}$FAILURES test(s) failed${NC}"
fi
exit $FAILURES
