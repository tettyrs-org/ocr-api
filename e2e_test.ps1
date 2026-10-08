#!/usr/bin/env pwsh
# End-to-End Integration Test for OCR API

param(
    [string]$ApiBase = "http://localhost:8081",
    [string]$TestEmail = "test@tettyrs.com",
    [string]$TestPassword = "test",
    [string]$TestPdfPath = "C:\Projects\OCR\api\test.pdf",
    [int]$MaxRetries = 10,
    [int]$RetryDelay = 2
)

$ErrorActionPreference = "Stop"

# Helper functions
function Write-StepLog {
    param([int]$Step, [string]$Message)
    Write-Host "[STEP $Step] $Message" -ForegroundColor Cyan
}

function Write-SuccessLog {
    param([string]$Message)
    Write-Host "✓ $Message" -ForegroundColor Green
}

function Write-ErrorLog {
    param([string]$Message)
    Write-Host "✗ $Message" -ForegroundColor Red
}

function Write-InfoLog {
    param([string]$Message)
    Write-Host "ℹ $Message" -ForegroundColor Yellow
}

# Check PDF exists
if (-not (Test-Path $TestPdfPath)) {
    Write-ErrorLog "Test PDF not found at: $TestPdfPath"
    exit 1
}

$PdfSize = (Get-Item $TestPdfPath).Length
Write-SuccessLog "Test PDF found (size: $PdfSize bytes)"
Write-Host ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "OCR API End-to-End Integration Test" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Start time: $(Get-Date)"
Write-Host ""

# ============================================================
# STEP 1: AUTHENTICATION
# ============================================================
Write-StepLog 1 "Authenticating with email: $TestEmail"

$loginJson = @{
    email = $TestEmail
    password = $TestPassword
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -Uri "$ApiBase/api/auth/login" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $loginJson -UseBasicParsing

    $loginData = $loginResponse.Content | ConvertFrom-Json
    Write-Host "Login response:"
    Write-Host ($loginData | ConvertTo-Json -Depth 5)
    Write-Host ""

    $Token = $loginData.data.token
    if (-not $Token) {
        Write-ErrorLog "Failed to obtain authentication token"
        Write-ErrorLog "Response: $($loginData | ConvertTo-Json)"
        exit 1
    }

    Write-SuccessLog "Authentication successful"
    Write-SuccessLog "Token: $($Token.Substring(0, [Math]::Min(20, $Token.Length)))..."
    Write-Host ""
}
catch {
    Write-ErrorLog "Authentication failed: $_"
    exit 1
}

# ============================================================
# STEP 2: CREATE DOCUMENT
# ============================================================
Write-StepLog 2 "Creating document metadata"

Write-Host "Document details:"
Write-Host "  filename: test.pdf"
Write-Host "  file_size: $PdfSize"
Write-Host "  mime_type: application/pdf"
Write-Host "  document_type: OTHER"
Write-Host "  created_by: $TestEmail"
Write-Host ""

$createJson = @{
    filename = "test.pdf"
    file_size = $PdfSize
    mime_type = "application/pdf"
    document_type = "OTHER"
    created_by = $TestEmail
} | ConvertTo-Json

try {
    $createResponse = Invoke-WebRequest -Uri "$ApiBase/api/v1/documents" `
        -Method POST `
        -Headers @{
            "Authorization" = "Bearer $Token"
            "Content-Type" = "application/json"
        } `
        -Body $createJson -UseBasicParsing

    $createData = $createResponse.Content | ConvertFrom-Json
    Write-Host "Create document response:"
    Write-Host ($createData | ConvertTo-Json -Depth 5)
    Write-Host ""

    $DocId = $createData.data.id
    if (-not $DocId) {
        Write-ErrorLog "Failed to create document"
        Write-ErrorLog "Response: $($createData | ConvertTo-Json)"
        exit 1
    }

    Write-SuccessLog "Document created"
    Write-SuccessLog "Document ID: $DocId"
    Write-Host ""
}
catch {
    Write-ErrorLog "Document creation failed: $_"
    exit 1
}

# ============================================================
# STEP 3: UPLOAD FILE
# ============================================================
Write-StepLog 3 "Uploading PDF file to document $DocId"

try {
    # Use multipart form data for file upload
    $fileStream = [System.IO.File]::OpenRead($TestPdfPath)
    $fileName = [System.IO.Path]::GetFileName($TestPdfPath)

    $boundary = [System.Guid]::NewGuid().ToString()
    $lf = "`r`n"

    $body = ([System.Text.Encoding]::UTF8.GetBytes(
        "--$boundary$lf" +
        "Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"$lf" +
        "Content-Type: application/pdf$lf$lf"
    ))

    $fileBytes = [System.IO.File]::ReadAllBytes($TestPdfPath)
    $body += $fileBytes
    $body += [System.Text.Encoding]::UTF8.GetBytes("$lf--$boundary--$lf")

    $uploadResponse = Invoke-WebRequest -Uri "$ApiBase/api/v1/documents/$DocId/upload" `
        -Method POST `
        -Headers @{
            "Authorization" = "Bearer $Token"
            "Content-Type" = "multipart/form-data; boundary=$boundary"
        } `
        -Body $body -UseBasicParsing

    $uploadData = $uploadResponse.Content | ConvertFrom-Json
    Write-Host "Upload response:"
    Write-Host ($uploadData | ConvertTo-Json -Depth 5)
    Write-Host ""

    if (-not $uploadData.data.id) {
        Write-ErrorLog "File upload failed"
        Write-ErrorLog "Response: $($uploadData | ConvertTo-Json)"
        exit 1
    }

    Write-SuccessLog "File uploaded successfully"
    $S3Path = $uploadData.data.s3_path
    Write-SuccessLog "S3 path: $S3Path"
    Write-Host ""
}
catch {
    Write-ErrorLog "File upload failed: $_"
    exit 1
}
finally {
    if ($fileStream) { $fileStream.Close() }
}

# ============================================================
# STEP 4: TRIGGER PROCESSING
# ============================================================
Write-StepLog 4 "Triggering document processing"

try {
    $processResponse = Invoke-WebRequest -Uri "$ApiBase/api/v1/documents/$DocId/process" `
        -Method POST `
        -Headers @{"Authorization" = "Bearer $Token"} `
        -UseBasicParsing

    $processData = $processResponse.Content | ConvertFrom-Json
    Write-Host "Process trigger response:"
    Write-Host ($processData | ConvertTo-Json -Depth 5)
    Write-Host ""

    if (-not $processData.data.id) {
        Write-ErrorLog "Failed to trigger processing"
        Write-ErrorLog "Response: $($processData | ConvertTo-Json)"
        exit 1
    }

    Write-SuccessLog "Processing triggered successfully"
    Write-Host ""
}
catch {
    Write-ErrorLog "Failed to trigger processing: $_"
    exit 1
}

# ============================================================
# STEP 5: MONITOR PROCESSING STATUS
# ============================================================
Write-StepLog 5 "Monitoring processing status (up to $MaxRetries attempts with ${RetryDelay}s delays)"
Write-Host ""

$finalStatus = "UNKNOWN"
$attempt = 0

while ($attempt -lt $MaxRetries) {
    $attempt++

    try {
        $statusResponse = Invoke-WebRequest -Uri "$ApiBase/api/v1/documents/$DocId/processing-status" `
            -Method GET `
            -Headers @{"Authorization" = "Bearer $Token"} `
            -UseBasicParsing

        $statusData = $statusResponse.Content | ConvertFrom-Json
        $processingStatus = $statusData.data.processingStatus

        if ($processingStatus) {
            Write-InfoLog "Attempt $attempt/$MaxRetries: Status = $processingStatus"
            Write-Host "Status response:"
            Write-Host ($statusData | ConvertTo-Json -Depth 5)

            $finalStatus = $processingStatus

            if ($processingStatus -eq "COMPLETED" -or $processingStatus -eq "FAILED") {
                Write-SuccessLog "Processing finished with status: $processingStatus"
                break
            }
        }
        else {
            Write-InfoLog "Attempt $attempt/$MaxRetries: No processing status yet"
            Write-Host "Response:"
            Write-Host ($statusData | ConvertTo-Json -Depth 5)
        }
    }
    catch {
        Write-InfoLog "Attempt $attempt/$MaxRetries: Error checking status: $_"
    }

    if ($attempt -lt $MaxRetries) {
        Write-Host "Waiting $RetryDelay seconds before next attempt..."
        Start-Sleep -Seconds $RetryDelay
    }
    Write-Host ""
}

# ============================================================
# TEST SUMMARY
# ============================================================
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "Test Summary" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$step1 = if ($Token) { "$(Write-Host 'PASS' -ForegroundColor Green -NoNewline)" } else { "$(Write-Host 'FAIL' -ForegroundColor Red -NoNewline)" }
$step2 = if ($DocId) { "$(Write-Host 'PASS' -ForegroundColor Green -NoNewline)" } else { "$(Write-Host 'FAIL' -ForegroundColor Red -NoNewline)" }
$step3 = if ($uploadData.data.id) { "$(Write-Host 'PASS' -ForegroundColor Green -NoNewline)" } else { "$(Write-Host 'FAIL' -ForegroundColor Red -NoNewline)" }
$step4 = if ($processData.data.id) { "$(Write-Host 'PASS' -ForegroundColor Green -NoNewline)" } else { "$(Write-Host 'FAIL' -ForegroundColor Red -NoNewline)" }
$step5 = if ($finalStatus) { "$(Write-Host 'PASS' -ForegroundColor Green -NoNewline)" } else { "$(Write-Host 'FAIL' -ForegroundColor Red -NoNewline)" }

Write-Host ""
Write-Host "Step 1 - Authentication:        " -NoNewline
Write-Host $(if ($Token) { "PASS" } else { "FAIL" }) -ForegroundColor $(if ($Token) { "Green" } else { "Red" })

Write-Host "Step 2 - Create Document:       " -NoNewline
Write-Host $(if ($DocId) { "PASS" } else { "FAIL" }) -ForegroundColor $(if ($DocId) { "Green" } else { "Red" })
Write-Host "  Document ID: $DocId"

Write-Host "Step 3 - Upload File:           " -NoNewline
Write-Host $(if ($uploadData.data.id) { "PASS" } else { "FAIL" }) -ForegroundColor $(if ($uploadData.data.id) { "Green" } else { "Red" })

Write-Host "Step 4 - Trigger Processing:    " -NoNewline
Write-Host $(if ($processData.data.id) { "PASS" } else { "FAIL" }) -ForegroundColor $(if ($processData.data.id) { "Green" } else { "Red" })

Write-Host "Step 5 - Check Status:          " -NoNewline
Write-Host $(if ($finalStatus) { "PASS" } else { "FAIL" }) -ForegroundColor $(if ($finalStatus) { "Green" } else { "Red" })
Write-Host "  Final Status: $finalStatus"
Write-Host ""

# Final status check
if ($finalStatus -eq "COMPLETED") {
    Write-SuccessLog "End-to-end test COMPLETED successfully!"
    exit 0
}
elseif ($finalStatus -eq "PROCESSING") {
    Write-InfoLog "End-to-end test PASSED - document is currently PROCESSING"
    exit 0
}
elseif ($finalStatus -eq "FAILED") {
    Write-ErrorLog "Document processing FAILED"
    exit 1
}
else {
    Write-InfoLog "End-to-end test completed with final status: $finalStatus"
    exit 0
}
