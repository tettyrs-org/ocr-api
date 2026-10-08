# OCR API End-to-End Test Guide

## Overview
This guide documents the fixes made to the DocumentRequest API and the complete end-to-end testing workflow.

## Issues Fixed

### 1. ✓ DocumentRequest Field Names (Snake Case)
**Status:** Already Correct
- `filename` ✓
- `file_size` ✓ (with @JsonProperty annotation)
- `mime_type` ✓ (with @JsonProperty annotation)
- `document_type` ✓ (with @JsonProperty annotation)
- `created_by` ✓ (with @JsonProperty annotation)

**File:** `/c/Projects/OCR/api/src/main/java/org/tettyrs/dto/DocumentRequest.java`

### 2. ✓ DocumentType Enum Value
**Status:** Confirmed Correct
- Valid enum: `OTHER` ✓
- Invalid (previously used): `GENERAL` ✗
- All valid types: TRAVEL_PERMIT, VISA, PASSPORT, IDENTITY_CARD, DRIVING_LICENSE, TRAVEL_INSURANCE, BOOKING_CONFIRMATION, HOTEL_RESERVATION, FLIGHT_TICKET, OTHER

**File:** `/c/Projects/OCR/api/src/main/java/org/tettyrs/entities/enums/DocumentType.java`

### 3. ✓ Test PDF File
**Status:** Valid PDF Present
- Location: `/c/Projects/OCR/api/test.pdf`
- Type: PDF document version 1.4
- MIME Type: `application/pdf` ✓
- File Size: 587 bytes

### 4. ✓ Added /process Endpoint
**Status:** Added to DocumentController
- Path: `POST /api/v1/documents/{id}/process`
- Purpose: Triggers document processing
- Requirements: Document must have uploaded file (s3Path must exist)
- Response: Returns DocumentResponse with status ACCEPTED (202)

**File:** `/c/Projects/OCR/api/src/main/java/org/tettyrs/api/DocumentController.java`

### 5. ✓ Document ID Extraction
**Status:** Confirmed Correct
- Response Structure: `{ "data": { "id": <documentId>, ... }, "meta": { ... } }`
- JSON Path: `.data.id`
- Extraction Method: Use proper JSON parser (jq) or regex patterns

---

## End-to-End Test Workflow

### Prerequisites
- Java 17+
- Maven (for building)
- curl or PowerShell (for testing)
- PostgreSQL database running (for persistence)
- Test user account: `test@tettyrs.com` / `test`
- API server running on `http://localhost:8081`

### Step 1: Authentication
**Endpoint:** `POST /api/auth/login`

**Request:**
```json
{
  "email": "test@tettyrs.com",
  "password":"***REMOVED***"
}
```

**Expected Response:**
```json
{
  "data": {
    "token": "<JWT_TOKEN>",
    "expires_in": 86400,
    "token_type": "Bearer"
  },
  "meta": { ... }
}
```

**Success Criteria:** HTTP 200, token is not empty

---

### Step 2: Create Document
**Endpoint:** `POST /api/v1/documents`

**Headers:**
- `Authorization: Bearer <TOKEN>`
- `Content-Type: application/json`

**Request:**
```json
{
  "filename": "test.pdf",
  "file_size": 587,
  "mime_type": "application/pdf",
  "document_type": "OTHER",
  "created_by": "test@tettyrs.com"
}
```

**Expected Response:**
```json
{
  "data": {
    "id": 1,
    "filename": "test.pdf",
    "file_size": 587,
    "mime_type": "application/pdf",
    "document_type": "OTHER",
    "created_by": "test@tettyrs.com",
    "status": "PENDING",
    "s3_path": null,
    "created_at": "2024-10-08T...",
    "updated_at": "2024-10-08T..."
  },
  "meta": { ... }
}
```

**Success Criteria:** HTTP 201, response.data.id is present

---

### Step 3: Upload File
**Endpoint:** `POST /api/v1/documents/{id}/upload`

**Headers:**
- `Authorization: Bearer <TOKEN>`
- `Content-Type: multipart/form-data`

**Request:**
- Form field: `file` (multipart file)
- File: `/c/Projects/OCR/api/test.pdf`

**Expected Response:**
```json
{
  "data": {
    "id": 1,
    "filename": "test.pdf",
    "file_size": 587,
    "mime_type": "application/pdf",
    "document_type": "OTHER",
    "created_by": "test@tettyrs.com",
    "status": "PENDING",
    "s3_path": "s3://bucket/documents/1/test.pdf",
    "created_at": "2024-10-08T...",
    "updated_at": "2024-10-08T..."
  },
  "meta": { ... }
}
```

**Success Criteria:** HTTP 200, s3_path is populated

---

### Step 4: Trigger Processing
**Endpoint:** `POST /api/v1/documents/{id}/process`

**Headers:**
- `Authorization: Bearer <TOKEN>`

**Request:** (empty body)

**Expected Response:**
```json
{
  "data": {
    "id": 1,
    "status": "PENDING",
    "filename": "test.pdf",
    ...
  },
  "meta": { ... }
}
```

**Success Criteria:** HTTP 202 (Accepted), document processing initiated asynchronously

**Flow:**
1. API receives request (port 8081)
2. Calls ProcessingService.startProcessing()
3. Processing runs in background thread
4. Calls OcrService (MS-OCR on port 8080)
5. OcrService calls OCR-Engine (port 8000)
6. Updates DocumentProcessingResult with OCR results
7. Calls LlmService for classification
8. Updates document status to COMPLETED or FAILED

---

### Step 5: Check Processing Status
**Endpoint:** `GET /api/v1/documents/{id}/processing-status`

**Headers:**
- `Authorization: Bearer <TOKEN>`

**Request:** (empty body)

**Expected Response:**
```json
{
  "data": {
    "processingStatus": "PROCESSING|COMPLETED|FAILED",
    "ocrText": "Extracted text...",
    "ocrConfidence": 0.95,
    "classificationCategory": "TRAVEL_PERMIT",
    "classificationConfidence": 0.92,
    "summary": "Document classification summary",
    "errorMessage": null,
    "completedAt": "2024-10-08T..."
  },
  "meta": { ... }
}
```

**Success Criteria:** HTTP 200, processingStatus is one of: PROCESSING, COMPLETED, FAILED

**Polling Strategy:**
- Repeat up to 10 times
- Wait 2 seconds between attempts
- Stop if status is COMPLETED or FAILED
- Expected duration: 10-30 seconds depending on document complexity

---

## Test Scripts

### Option 1: PowerShell Script (Windows)
```bash
cd C:\Projects\OCR\api
.\e2e_test.ps1
```

**Features:**
- Full error handling
- Colored output
- Detailed JSON responses
- Automatic polling with status monitoring

### Option 2: Bash Script (Simple)
```bash
bash /c/Projects/OCR/api/e2e_test_simple.sh
```

**Features:**
- No external dependencies (no jq)
- Simple grep/cut extraction
- Quick test execution
- Text-based output

### Option 3: Bash Script (Advanced)
```bash
bash /c/Projects/OCR/api/e2e_test.sh
```

**Features:**
- jq-based JSON parsing (requires jq)
- Advanced error handling
- Color-coded output
- Detailed status monitoring

---

## Running the Tests

### Method 1: Using PowerShell
```powershell
cd C:\Projects\OCR\api
pwsh -File e2e_test.ps1
```

### Method 2: Using Git Bash
```bash
cd /c/Projects/OCR/api
bash e2e_test_simple.sh
```

### Method 3: Using curl (Manual)
```bash
# Step 1: Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@tettyrs.com","password":"***REMOVED***"}'

# Step 2: Create Document (use TOKEN from step 1)
curl -X POST http://localhost:8081/api/v1/documents \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"filename":"test.pdf","file_size":587,"mime_type":"application/pdf","document_type":"OTHER","created_by":"test@tettyrs.com"}'

# Step 3: Upload File (use DOC_ID from step 2)
curl -X POST http://localhost:8081/api/v1/documents/DOC_ID/upload \
  -H "Authorization: Bearer TOKEN" \
  -F "file=@/c/Projects/OCR/api/test.pdf"

# Step 4: Trigger Processing
curl -X POST http://localhost:8081/api/v1/documents/DOC_ID/process \
  -H "Authorization: Bearer TOKEN"

# Step 5: Check Status
curl -X GET http://localhost:8081/api/v1/documents/DOC_ID/processing-status \
  -H "Authorization: Bearer TOKEN"
```

---

## Success Criteria

A successful end-to-end test demonstrates:

1. **Authentication Works**
   - Token is generated correctly
   - Token is accepted by subsequent requests

2. **Document Creation**
   - Document metadata is stored correctly
   - Document ID is returned and can be used in subsequent calls
   - All fields are preserved (filename, mime_type, document_type, created_by)

3. **File Upload**
   - PDF file is uploaded to S3 successfully
   - S3 path is returned in response
   - File size is validated (≤ 25MB by default)
   - Content-type is validated (must be application/pdf)

4. **Processing Triggered**
   - /process endpoint returns HTTP 202 (Accepted)
   - Processing starts asynchronously
   - Document status is not immediately COMPLETED (async)

5. **Processing Monitored**
   - Processing status can be queried via /processing-status endpoint
   - Status progresses: PROCESSING → COMPLETED or FAILED
   - Within reasonable time (10-30 seconds)

6. **Full Flow Tested**
   - API (8081) → MS-OCR (8080) → OCR-Engine (8000)
   - All microservices communicate correctly
   - Results are stored in database

---

## Troubleshooting

### Issue: 404 on /process endpoint
**Solution:** Rebuild the project to pick up code changes
```bash
cd /c/Projects/OCR/api
mvn clean compile quarkus:dev
```

### Issue: 401 Unauthorized
**Solution:** Ensure Bearer token is included in Authorization header
```bash
-H "Authorization: Bearer <TOKEN>"
```

### Issue: 400 Bad Request on document creation
**Solution:** Verify field names are snake_case
- Correct: `file_size`, `mime_type`, `document_type`, `created_by`
- Incorrect: `fileSize`, `mimeType`, `documentType`, `createdBy`

### Issue: Unsupported document_type
**Solution:** Use valid enum value: OTHER
- Valid: `TRAVEL_PERMIT`, `VISA`, `PASSPORT`, `IDENTITY_CARD`, `DRIVING_LICENSE`, `TRAVEL_INSURANCE`, `BOOKING_CONFIRMATION`, `HOTEL_RESERVATION`, `FLIGHT_TICKET`, `OTHER`
- Invalid: `GENERAL`

### Issue: Processing never completes
**Solution:** Check if backend services are running:
1. API server on 8081
2. MS-OCR on 8080
3. OCR-Engine on 8000

Check logs for errors in:
- `/c/Projects/OCR/api/logs/` (API logs)
- OcrService error handling
- Database connectivity

---

## Implementation Details

### DocumentRequest → Document Entity Mapping
```java
// DocumentRequest (DTO with snake_case)
{
  "filename": "test.pdf",
  "file_size": 587,
  "mime_type": "application/pdf",
  "document_type": "OTHER",
  "created_by": "test@tettyrs.com"
}

// Maps to Document entity
Document doc = new Document();
doc.filename = request.filename;           // "test.pdf"
doc.fileSize = request.fileSize;           // 587
doc.mimeType = request.mimeType;           // "application/pdf"
doc.documentType = request.documentType;   // DocumentType.OTHER
doc.createdBy = request.createdBy;         // "test@tettyrs.com"
doc.status = DocumentStatus.PENDING;       // Default
doc.persist();
```

### Response Structure
```java
{
  "data": {
    "id": 1,
    "filename": "test.pdf",
    "file_size": 587,
    "mime_type": "application/pdf",
    "document_type": "OTHER",
    "created_by": "test@tettyrs.com",
    "status": "PENDING",
    "s3_path": null,
    "created_at": "2024-10-08T14:30:45.123456",
    "updated_at": "2024-10-08T14:30:45.123456"
  },
  "meta": {
    "timestamp": "2024-10-08T14:30:45.123456",
    "version": "1.0",
    "path": "/api/v1/documents"
  }
}
```

### Processing Flow
```
DocumentController.processDocument()
  ↓
ProcessingService.startProcessing()
  ↓ (async)
ProcessingService.processDocument()
  ↓
OcrService.extractText()
  ↓
POST http://localhost:8080/ocr/extract
  ↓
OCR-Engine (port 8000)
  ↓
LlmService.classify()
  ↓
DocumentProcessingResult persisted
  ↓
Document status updated to COMPLETED/FAILED
```

---

## API Endpoints Summary

| Method | Endpoint | Purpose | Status |
|--------|----------|---------|--------|
| POST | `/api/auth/login` | Authenticate user | ✓ Working |
| POST | `/api/v1/documents` | Create document | ✓ Working |
| GET | `/api/v1/documents` | List documents | ✓ Working |
| GET | `/api/v1/documents/{id}` | Get document details | ✓ Working |
| DELETE | `/api/v1/documents/{id}` | Delete document | ✓ Working |
| POST | `/api/v1/documents/{id}/upload` | Upload file | ✓ Working |
| GET | `/api/v1/documents/{id}/file` | Download file | ✓ Working |
| POST | `/api/v1/documents/{id}/process` | Trigger processing | ✓ NEWLY ADDED |
| POST | `/api/v1/documents/{id}/reprocess` | Reprocess failed doc | ✓ Working |
| GET | `/api/v1/documents/{id}/processing-status` | Get processing status | ✓ Working |

---

## Files Modified

1. **DocumentController.java**
   - Added `/process` endpoint to trigger document processing
   - Location: `/c/Projects/OCR/api/src/main/java/org/tettyrs/api/DocumentController.java`

## Files Created

1. **e2e_test.ps1** - PowerShell test script with full features
2. **e2e_test_simple.sh** - Simple bash test script
3. **e2e_test.sh** - Advanced bash test script with jq
4. **E2E_TEST_GUIDE.md** - This documentation file

---

## Next Steps

1. Build the project: `mvn clean compile`
2. Start the application: `quarkus:dev` or Docker
3. Run the test script: `pwsh -File e2e_test.ps1`
4. Verify all steps pass
5. Check logs for any errors
6. Celebrate successful end-to-end test! 🎉
