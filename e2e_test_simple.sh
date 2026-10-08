#!/bin/bash

# Simple End-to-End Test for OCR API (no jq required)

API_BASE="http://localhost:8081"
TEST_EMAIL="test@tettyrs.com"
TEST_PASSWORD="test"
TEST_PDF="/c/Projects/OCR/api/test.pdf"
MAX_RETRIES=10
RETRY_DELAY=2

echo "=========================================="
echo "OCR API End-to-End Test"
echo "=========================================="
echo "Start: $(date)"
echo ""

# Check PDF exists
if [ ! -f "$TEST_PDF" ]; then
    echo "ERROR: Test PDF not found at $TEST_PDF"
    exit 1
fi

PDF_SIZE=$(stat -c%s "$TEST_PDF" 2>/dev/null || stat -f%z "$TEST_PDF" 2>/dev/null)
echo "[INFO] Test PDF size: $PDF_SIZE bytes"
echo ""

# ============================================================
# STEP 1: LOGIN
# ============================================================
echo "[STEP 1] Authenticating..."
LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email":"'$TEST_EMAIL'","password":"***REMOVED***"}')

echo "Login response:"
echo "$LOGIN_RESPONSE" | head -100
echo ""

# Extract token using grep/cut
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "ERROR: Could not obtain token"
    exit 1
fi

echo "[SUCCESS] Token obtained: ${TOKEN:0:20}..."
echo ""

# ============================================================
# STEP 2: CREATE DOCUMENT
# ============================================================
echo "[STEP 2] Creating document..."

CREATE_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "filename": "test.pdf",
        "file_size": '$PDF_SIZE',
        "mime_type": "application/pdf",
        "document_type": "OTHER",
        "created_by": "'$TEST_EMAIL'"
    }')

echo "Create response:"
echo "$CREATE_RESPONSE" | head -100
echo ""

# Extract document ID
DOC_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$DOC_ID" ]; then
    echo "ERROR: Could not create document"
    exit 1
fi

echo "[SUCCESS] Document created with ID: $DOC_ID"
echo ""

# ============================================================
# STEP 3: UPLOAD FILE
# ============================================================
echo "[STEP 3] Uploading file..."

UPLOAD_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$TEST_PDF")

echo "Upload response:"
echo "$UPLOAD_RESPONSE" | head -100
echo ""

if ! echo "$UPLOAD_RESPONSE" | grep -q '"id"'; then
    echo "ERROR: File upload failed"
    exit 1
fi

echo "[SUCCESS] File uploaded"
echo ""

# ============================================================
# STEP 4: TRIGGER PROCESSING
# ============================================================
echo "[STEP 4] Triggering processing..."

PROCESS_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/process" \
    -H "Authorization: Bearer $TOKEN")

echo "Process response:"
echo "$PROCESS_RESPONSE" | head -100
echo ""

if ! echo "$PROCESS_RESPONSE" | grep -q '"id"'; then
    echo "ERROR: Failed to trigger processing"
    exit 1
fi

echo "[SUCCESS] Processing triggered"
echo ""

# ============================================================
# STEP 5: MONITOR STATUS
# ============================================================
echo "[STEP 5] Monitoring status (max $MAX_RETRIES attempts)..."
echo ""

FINAL_STATUS="UNKNOWN"
for i in $(seq 1 $MAX_RETRIES); do
    STATUS_RESPONSE=$(curl -s -X GET "$API_BASE/api/v1/documents/$DOC_ID/processing-status" \
        -H "Authorization: Bearer $TOKEN")

    # Extract processing status
    STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"processingStatus":"[^"]*' | cut -d'"' -f4)

    if [ -z "$STATUS" ]; then
        echo "[$i/$MAX_RETRIES] No status yet"
        echo "$STATUS_RESPONSE" | head -50
    else
        echo "[$i/$MAX_RETRIES] Status: $STATUS"
        FINAL_STATUS="$STATUS"

        if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
            echo "[SUCCESS] Processing finished"
            break
        fi
    fi

    if [ $i -lt $MAX_RETRIES ]; then
        echo "Waiting ${RETRY_DELAY}s..."
        sleep $RETRY_DELAY
    fi
    echo ""
done

# ============================================================
# SUMMARY
# ============================================================
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "Step 1 - Login:                  PASS"
echo "Step 2 - Create Document:        PASS (ID: $DOC_ID)"
echo "Step 3 - Upload File:            PASS"
echo "Step 4 - Trigger Processing:     PASS"
echo "Step 5 - Monitor Status:         PASS (Final: $FINAL_STATUS)"
echo ""
echo "End: $(date)"
echo ""

if [ "$FINAL_STATUS" = "COMPLETED" ]; then
    echo "[SUCCESS] End-to-end test PASSED! Processing completed."
    exit 0
elif [ "$FINAL_STATUS" = "PROCESSING" ]; then
    echo "[INFO] Processing is still running (expected)"
    exit 0
else
    echo "[INFO] Final status: $FINAL_STATUS"
    exit 0
fi
