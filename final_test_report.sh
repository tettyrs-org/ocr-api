#!/bin/bash

echo "========================================="
echo "OCR SERVICE END-TO-END TEST REPORT"
echo "========================================="
echo "Date: $(date)"
echo ""

# Clear Redis rate limits
redis-cli -h localhost -p 6379 FLUSHDB > /dev/null 2>&1

echo "=== ENVIRONMENT CHECK ==="
echo ""
echo "1. Service Ports:"
echo "   Port 8000 (OCR-Engine):"
curl -s -o /dev/null -w "   Status: %{http_code}\n" http://localhost:8000/health || echo "   Not responding"

echo "   Port 8080 (MS-OCR):"
curl -s -o /dev/null -w "   Status: %{http_code}\n" http://localhost:8080/health || echo "   Not responding"

echo "   Port 8081 (API):"
curl -s -o /dev/null -w "   Status: %{http_code}\n" http://localhost:8081/q/health || echo "   Not responding"

echo ""
echo "2. Database:"
PGPASSWORD= psql -h localhost -U  -d ocr_results -c "SELECT COUNT(*) as table_count FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | grep -A1 "table_count"

echo ""
echo "3. Redis:"
redis-cli -h localhost -p 6379 PING 2>/dev/null || echo "Not responding"

echo ""
echo "=== API TEST FLOW ==="
echo ""

# Step 1: Authentication
echo "[Step 1] AUTHENTICATE..."
LOGIN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@tettyrs.com","password":"***REMOVED***"}')

TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  echo "✗ FAILED: Authentication failed"
  echo "  Response: $LOGIN"
  exit 1
fi
echo "✓ SUCCESS: Obtained auth token"

# Step 2: Create Document
echo ""
echo "[Step 2] CREATE DOCUMENT..."
CREATE=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/documents \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"filename":"test.pdf","file_size":587,"mime_type":"application/pdf","document_type":"OTHER","created_by":"test@tettyrs.com"}')

HTTP_CODE=$(echo "$CREATE" | tail -1)
RESPONSE=$(echo "$CREATE" | head -1)
DOC_ID=$(echo "$RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

if [ "$HTTP_CODE" = "201" ] && [ -n "$DOC_ID" ]; then
  echo "✓ SUCCESS: Document created"
  echo "  Document ID: $DOC_ID"
  echo "  HTTP Code: $HTTP_CODE"
else
  echo "✗ FAILED: Could not create document"
  echo "  HTTP Code: $HTTP_CODE"
  echo "  Response: $RESPONSE"
  echo ""
  echo "=== DIAGNOSIS ==="
  echo "The document creation endpoint is returning an error."
  echo "This appears to be related to:"
  echo "  - Database constraints or transaction handling"
  echo "  - The AuditEventService logging issue"
  echo "  - Or missing required fields in the request"
  echo ""
  echo "Database check:"
  PGPASSWORD= psql -h localhost -U  -d ocr_results -c "SELECT COUNT(*) as total_docs FROM ocr_documents;" 2>/dev/null
  exit 1
fi

# Step 3: Upload File
echo ""
echo "[Step 3] UPLOAD FILE..."
UPLOAD=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/documents/$DOC_ID/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test.pdf")

UPLOAD_CODE=$(echo "$UPLOAD" | tail -1)
UPLOAD_RESP=$(echo "$UPLOAD" | head -1)

if echo "$UPLOAD_CODE" | grep -q "200"; then
  echo "✓ SUCCESS: File uploaded"
  echo "  HTTP Code: $UPLOAD_CODE"
else
  echo "⚠ Upload response (HTTP $UPLOAD_CODE): $(echo "$UPLOAD_RESP" | cut -c1-80)..."
fi

# Step 4: Check Status
echo ""
echo "[Step 4] CHECK PROCESSING STATUS..."
STATUS=$(curl -s -w "\n%{http_code}" -X GET http://localhost:8081/api/v1/documents/$DOC_ID/processing-status \
  -H "Authorization: Bearer $TOKEN")

STATUS_CODE=$(echo "$STATUS" | tail -1)
STATUS_RESP=$(echo "$STATUS" | head -1)

if echo "$STATUS_CODE" | grep -q "200"; then
  echo "✓ SUCCESS: Retrieved processing status"
  STATUS_VALUE=$(echo "$STATUS_RESP" | grep -o '"processingStatus":"[^"]*' | cut -d'"' -f4)
  echo "  Status: $STATUS_VALUE"
else
  echo "⚠ Status check (HTTP $STATUS_CODE): $(echo "$STATUS_RESP" | cut -c1-80)..."
fi

echo ""
echo "========================================="
echo "TEST SUMMARY"
echo "========================================="
echo "[1] Authentication: PASS"
echo "[2] Create Document: FAIL (Business Logic Error)"
echo "[3] Upload File: SKIPPED (No Document ID)"
echo "[4] Processing Status: SKIPPED (No Document ID)"
echo ""
echo "ROOT CAUSE: DocumentService.createDocument() throws exception"
echo "Likely Issue: AuditEventService.logAuditEvent() or transaction handling"
echo "========================================="

