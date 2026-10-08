#!/bin/bash
# Manual Step-by-Step E2E Test Using curl
# This script shows exactly what API calls are made and can be easily modified

set -e

API_BASE="http://localhost:8081"
TEST_EMAIL="test@tettyrs.com"
TEST_PASSWORD="test"
TEST_PDF="/c/Projects/OCR/api/test.pdf"

echo "=========================================="
echo "Manual Step-by-Step API Test"
echo "=========================================="
echo ""

# Get PDF file size for the request
PDF_SIZE=$(stat -c%s "$TEST_PDF" 2>/dev/null || stat -f%z "$TEST_PDF" 2>/dev/null)

# ============================================================
# STEP 1: LOGIN - Get Authentication Token
# ============================================================
echo "STEP 1: Login to get authentication token"
echo "=========================================="
echo ""
echo "Request:"
echo "  POST $API_BASE/api/auth/login"
echo "  Headers:"
echo "    Content-Type: application/json"
echo "  Body:"
echo '    {"email":"'$TEST_EMAIL'","password":"***REMOVED***"}'
echo ""

LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"'$TEST_EMAIL'","password":"***REMOVED***"}')

echo "Response:"
echo "$LOGIN_RESPONSE"
echo ""

# Extract token
TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "ERROR: Could not extract token from response"
  exit 1
fi

echo "Extracted Token: ${TOKEN:0:30}..."
echo ""
echo "Press Enter to continue to Step 2..."
read

# ============================================================
# STEP 2: CREATE DOCUMENT - Register document metadata
# ============================================================
echo ""
echo "STEP 2: Create document (register metadata)"
echo "=========================================="
echo ""
echo "Request:"
echo "  POST $API_BASE/api/v1/documents"
echo "  Headers:"
echo "    Authorization: Bearer $TOKEN"
echo "    Content-Type: application/json"
echo "  Body:"
echo "    {"
echo '      "filename": "test.pdf",'
echo "      \"file_size\": $PDF_SIZE,"
echo '      "mime_type": "application/pdf",'
echo '      "document_type": "OTHER",'
echo '      "created_by": "'$TEST_EMAIL'"'
echo "    }"
echo ""

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

echo "Response:"
echo "$CREATE_RESPONSE"
echo ""

# Extract document ID
DOC_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ -z "$DOC_ID" ]; then
  echo "ERROR: Could not extract document ID from response"
  exit 1
fi

echo "Extracted Document ID: $DOC_ID"
echo ""
echo "Press Enter to continue to Step 3..."
read

# ============================================================
# STEP 3: UPLOAD FILE - Upload PDF to S3
# ============================================================
echo ""
echo "STEP 3: Upload PDF file"
echo "=========================================="
echo ""
echo "Request:"
echo "  POST $API_BASE/api/v1/documents/$DOC_ID/upload"
echo "  Headers:"
echo "    Authorization: Bearer $TOKEN"
echo "    Content-Type: multipart/form-data"
echo "  Body:"
echo "    file=<binary PDF data from $TEST_PDF>"
echo ""

UPLOAD_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TEST_PDF")

echo "Response:"
echo "$UPLOAD_RESPONSE"
echo ""

# Check for success
if ! echo "$UPLOAD_RESPONSE" | grep -q '"id"'; then
  echo "ERROR: Upload failed"
  exit 1
fi

S3_PATH=$(echo "$UPLOAD_RESPONSE" | grep -o '"s3_path":"[^"]*' | cut -d'"' -f4)
echo "Extracted S3 Path: $S3_PATH"
echo ""
echo "Press Enter to continue to Step 4..."
read

# ============================================================
# STEP 4: TRIGGER PROCESSING - Start async OCR processing
# ============================================================
echo ""
echo "STEP 4: Trigger document processing"
echo "=========================================="
echo ""
echo "Request:"
echo "  POST $API_BASE/api/v1/documents/$DOC_ID/process"
echo "  Headers:"
echo "    Authorization: Bearer $TOKEN"
echo "  Body: (empty)"
echo ""

PROCESS_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/process" \
  -H "Authorization: Bearer $TOKEN")

echo "Response:"
echo "$PROCESS_RESPONSE"
echo ""

# Check for success
if ! echo "$PROCESS_RESPONSE" | grep -q '"id"'; then
  echo "ERROR: Failed to trigger processing"
  exit 1
fi

echo "Processing triggered successfully (HTTP 202 Accepted)"
echo ""
echo "Press Enter to continue to Step 5..."
read

# ============================================================
# STEP 5: CHECK PROCESSING STATUS - Monitor progress
# ============================================================
echo ""
echo "STEP 5: Check processing status"
echo "=========================================="
echo ""
echo "Request:"
echo "  GET $API_BASE/api/v1/documents/$DOC_ID/processing-status"
echo "  Headers:"
echo "    Authorization: Bearer $TOKEN"
echo ""

echo "Checking status (this may show PROCESSING if still running)..."
echo ""

STATUS_RESPONSE=$(curl -s -X GET "$API_BASE/api/v1/documents/$DOC_ID/processing-status" \
  -H "Authorization: Bearer $TOKEN")

echo "Response:"
echo "$STATUS_RESPONSE"
echo ""

# Extract processing status
STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"processingStatus":"[^"]*' | cut -d'"' -f4)

echo "Processing Status: $STATUS"
echo ""

if [ "$STATUS" = "COMPLETED" ]; then
  echo "✓ Processing completed successfully!"
  echo ""
  echo "You can view the full response above, which includes:"
  echo "  - ocrText: Extracted text from document"
  echo "  - ocrConfidence: Confidence score for OCR extraction"
  echo "  - classificationCategory: Document type classification"
  echo "  - classificationConfidence: Confidence score for classification"
  echo "  - summary: AI-generated summary"
  echo "  - completedAt: Timestamp when processing finished"
elif [ "$STATUS" = "PROCESSING" ]; then
  echo "⏳ Processing is still running..."
  echo "   Try checking again in a few seconds"
elif [ "$STATUS" = "FAILED" ]; then
  echo "❌ Processing failed!"
  echo "   Check the errorMessage field in the response above"
else
  echo "ℹ️  Current status: $STATUS"
fi

echo ""
echo "=========================================="
echo "Test Complete"
echo "=========================================="
echo ""
echo "Summary of calls made:"
echo "  1. POST /api/auth/login → Token: ${TOKEN:0:30}..."
echo "  2. POST /api/v1/documents → Document ID: $DOC_ID"
echo "  3. POST /api/v1/documents/$DOC_ID/upload → S3 Path: $S3_PATH"
echo "  4. POST /api/v1/documents/$DOC_ID/process → Triggered"
echo "  5. GET /api/v1/documents/$DOC_ID/processing-status → Status: $STATUS"
echo ""
echo "Repeat Step 5 to poll for status updates"
echo ""
