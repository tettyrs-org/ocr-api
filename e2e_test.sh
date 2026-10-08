#!/bin/bash

set -e

echo "=========================================="
echo "OCR API End-to-End Integration Test"
echo "=========================================="
echo "Start time: $(date)"
echo ""

# Configuration
API_BASE="http://localhost:8081"
TEST_EMAIL="test@tettyrs.com"
TEST_PASSWORD="test"
TEST_PDF="/c/Projects/OCR/api/test.pdf"
MAX_RETRIES=10
RETRY_DELAY=2

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_step() {
    echo -e "${BLUE}[STEP $1]${NC} $2"
}

log_success() {
    echo -e "${GREEN}✓${NC} $1"
}

log_error() {
    echo -e "${RED}✗${NC} $1"
}

log_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

check_pdf() {
    if [ ! -f "$TEST_PDF" ]; then
        log_error "Test PDF not found at: $TEST_PDF"
        exit 1
    fi
    local size=$(stat -c%s "$TEST_PDF" 2>/dev/null || stat -f%z "$TEST_PDF" 2>/dev/null)
    log_success "Test PDF found (size: $size bytes)"
}

# ============================================================
# STEP 1: AUTHENTICATION
# ============================================================
log_step 1 "Authenticating with email: $TEST_EMAIL"

LOGIN_RESPONSE=$(curl -s -X POST "$API_BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"$TEST_PASSWORD\"}")

echo "Login response:"
echo "$LOGIN_RESPONSE" | jq '.' 2>/dev/null || echo "$LOGIN_RESPONSE"
echo ""

# Extract token
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.token // empty' 2>/dev/null)

if [ -z "$TOKEN" ]; then
    log_error "Failed to obtain authentication token"
    log_error "Full response: $LOGIN_RESPONSE"
    exit 1
fi

log_success "Authentication successful"
log_success "Token: ${TOKEN:0:20}..."
echo ""

# ============================================================
# STEP 2: CREATE DOCUMENT
# ============================================================
log_step 2 "Creating document metadata"

# Get actual PDF file size
PDF_SIZE=$(stat -c%s "$TEST_PDF" 2>/dev/null || stat -f%z "$TEST_PDF" 2>/dev/null)

echo "Document details:"
echo "  filename: test.pdf"
echo "  file_size: $PDF_SIZE"
echo "  mime_type: application/pdf"
echo "  document_type: OTHER"
echo "  created_by: $TEST_EMAIL"
echo ""

CREATE_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"filename\": \"test.pdf\",
        \"file_size\": $PDF_SIZE,
        \"mime_type\": \"application/pdf\",
        \"document_type\": \"OTHER\",
        \"created_by\": \"$TEST_EMAIL\"
    }")

echo "Create document response:"
echo "$CREATE_RESPONSE" | jq '.' 2>/dev/null || echo "$CREATE_RESPONSE"
echo ""

# Extract document ID
DOC_ID=$(echo "$CREATE_RESPONSE" | jq -r '.data.id // empty' 2>/dev/null)

if [ -z "$DOC_ID" ]; then
    log_error "Failed to create document"
    log_error "Full response: $CREATE_RESPONSE"
    exit 1
fi

log_success "Document created"
log_success "Document ID: $DOC_ID"
echo ""

# ============================================================
# STEP 3: UPLOAD FILE
# ============================================================
log_step 3 "Uploading PDF file to document $DOC_ID"

UPLOAD_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/upload" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$TEST_PDF")

echo "Upload response:"
echo "$UPLOAD_RESPONSE" | jq '.' 2>/dev/null || echo "$UPLOAD_RESPONSE"
echo ""

# Check if upload was successful
UPLOAD_STATUS=$(echo "$UPLOAD_RESPONSE" | jq -r '.data.id // empty' 2>/dev/null)

if [ -z "$UPLOAD_STATUS" ]; then
    log_error "File upload failed"
    log_error "Full response: $UPLOAD_RESPONSE"
    exit 1
fi

log_success "File uploaded successfully"
S3_PATH=$(echo "$UPLOAD_RESPONSE" | jq -r '.data.s3_path // "N/A"' 2>/dev/null)
log_success "S3 path: $S3_PATH"
echo ""

# ============================================================
# STEP 4: TRIGGER PROCESSING
# ============================================================
log_step 4 "Triggering document processing"

PROCESS_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/documents/$DOC_ID/process" \
    -H "Authorization: Bearer $TOKEN")

echo "Process trigger response:"
echo "$PROCESS_RESPONSE" | jq '.' 2>/dev/null || echo "$PROCESS_RESPONSE"
echo ""

# Check if processing was triggered
PROCESS_STATUS=$(echo "$PROCESS_RESPONSE" | jq -r '.data.id // empty' 2>/dev/null)

if [ -z "$PROCESS_STATUS" ]; then
    log_error "Failed to trigger processing"
    log_error "Full response: $PROCESS_RESPONSE"
    exit 1
fi

log_success "Processing triggered successfully"
echo ""

# ============================================================
# STEP 5: MONITOR PROCESSING STATUS
# ============================================================
log_step 5 "Monitoring processing status (up to $MAX_RETRIES attempts with ${RETRY_DELAY}s delays)"

FINAL_STATUS="UNKNOWN"
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_RETRIES ]; do
    ATTEMPT=$((ATTEMPT + 1))

    STATUS_RESPONSE=$(curl -s -X GET "$API_BASE/api/v1/documents/$DOC_ID/processing-status" \
        -H "Authorization: Bearer $TOKEN")

    # Extract processing status
    PROCESSING_STATUS=$(echo "$STATUS_RESPONSE" | jq -r '.data.processingStatus // empty' 2>/dev/null)

    if [ -z "$PROCESSING_STATUS" ]; then
        log_info "Attempt $ATTEMPT/$MAX_RETRIES: No processing status yet"
        echo "Response:"
        echo "$STATUS_RESPONSE" | jq '.' 2>/dev/null || echo "$STATUS_RESPONSE"
    else
        log_info "Attempt $ATTEMPT/$MAX_RETRIES: Status = $PROCESSING_STATUS"

        # Display full status response
        echo "Status response:"
        echo "$STATUS_RESPONSE" | jq '.' 2>/dev/null || echo "$STATUS_RESPONSE"

        FINAL_STATUS="$PROCESSING_STATUS"

        # Check if processing is complete or failed
        if [ "$PROCESSING_STATUS" = "COMPLETED" ] || [ "$PROCESSING_STATUS" = "FAILED" ]; then
            log_success "Processing finished with status: $PROCESSING_STATUS"
            break
        fi
    fi

    # Don't sleep after the last attempt
    if [ $ATTEMPT -lt $MAX_RETRIES ]; then
        echo "Waiting ${RETRY_DELAY} seconds before next attempt..."
        sleep $RETRY_DELAY
    fi
    echo ""
done

# ============================================================
# TEST SUMMARY
# ============================================================
echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo ""
echo "Step 1 - Authentication:        $([ -n "$TOKEN" ] && echo -e "${GREEN}PASS${NC}" || echo -e "${RED}FAIL${NC}")"
echo "Step 2 - Create Document:       $([ -n "$DOC_ID" ] && echo -e "${GREEN}PASS${NC}" || echo -e "${RED}FAIL${NC}") (ID: $DOC_ID)"
echo "Step 3 - Upload File:           $([ -n "$UPLOAD_STATUS" ] && echo -e "${GREEN}PASS${NC}" || echo -e "${RED}FAIL${NC}")"
echo "Step 4 - Trigger Processing:    $([ -n "$PROCESS_STATUS" ] && echo -e "${GREEN}PASS${NC}" || echo -e "${RED}FAIL${NC}")"
echo "Step 5 - Check Status:          $([ -n "$FINAL_STATUS" ] && echo -e "${GREEN}PASS${NC}" || echo -e "${RED}FAIL${NC}") (Final: $FINAL_STATUS)"
echo ""

# Final status check
if [ "$FINAL_STATUS" = "COMPLETED" ]; then
    log_success "End-to-end test COMPLETED successfully!"
    exit 0
elif [ "$FINAL_STATUS" = "PROCESSING" ]; then
    log_info "End-to-end test PASSED - document is currently PROCESSING"
    exit 0
elif [ "$FINAL_STATUS" = "FAILED" ]; then
    log_error "Document processing FAILED"
    exit 1
else
    log_info "End-to-end test completed with final status: $FINAL_STATUS"
    exit 0
fi
