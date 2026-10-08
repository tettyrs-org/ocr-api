# Document ID UUID Migration

## Summary
The Document entity has been migrated from sequential Long IDs to random UUID v4 for enhanced production security. This change prevents ID enumeration attacks and makes the API more secure for sensitive operations.

## Benefits
- **Security**: Random UUIDs are non-sequential and non-enumerable, preventing ID guessing attacks
- **Privacy**: Users cannot enumerate or discover other documents by incrementing IDs
- **Scalability**: UUIDs work better in distributed systems
- **Industry Standard**: Most modern production APIs use UUIDs for sensitive resources

## Files Modified

### Entities (6 files)
1. **Document.java** - Changed `id` from `Long` to `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`
2. **DocumentProcessingResult.java** - Changed `documentId` from `Long` to `UUID`
3. **DocumentVerification.java** - Changed `documentId` from `Long` to `UUID`
4. **ExtractionCallback.java** - Changed `documentId` from `Long` to `UUID`
5. **TelemetryTiming.java** - Changed `documentId` from `Long` to `UUID`
6. **Document.java** - Also updated `proccesingResultId` from `Long` to `UUID`

### DTOs (8 files)
1. **DocumentResponse.java** - Changed `id` from `Long` to `UUID`
2. **CallbackResponse.java** - Changed `documentId` from `Long` to `UUID`
3. **CallbackRequest.java** - Changed `documentId` from `Long` to `UUID`
4. **VerificationResponse.java** - Changed `documentId` and nested `DocumentInfo.id` to `UUID`
5. **TelemetryResponse.java** - Changed `documentId` from `Long` to `UUID`
6. **TimingEntry.java** - Changed `document_id` from `Long` to `UUID`
7. **TravelAssignmentRequest.java** - Changed `documentId` from `Long` to `UUID`
8. **TravelAssignmentResponse.java** - Changed `documentId` from `Long` to `UUID`

### Controllers (8 files)
1. **DocumentController.java** - Updated all `@PathParam("id")` from `Long` to `UUID`
2. **AuditEventController.java** - Updated `@PathParam("documentId")` from `Long` to `UUID`
3. **CallbackController.java** - Updated `@PathParam("documentId")` from `Long` to `UUID`
4. **GlobalCallbackController.java** - Updated to support `UUID documentId` in requests
5. **TelemetryController.java** - Updated all `@PathParam("documentId")` from `Long` to `UUID`
6. **VerificationController.java** - Updated all `@PathParam("documentId")` from `Long` to `UUID`
7. **InternalCallbackController.java** - Updated `@PathParam("id")` from `Long` to `UUID`
8. **TravelAssignmentController.java** - Remains with `Long` for assignment ID (not document ID)

### Services (6 files)
1. **DocumentService.java** - Updated all methods taking `Long documentId` to use `UUID`
   - `getDocumentById(UUID id)`
   - `deleteDocument(UUID id)`
   - `updateDocumentStatus(UUID id, DocumentStatus status)`
   - `uploadFile(UUID documentId, ...)`

2. **AuditEventService.java** - Updated methods to use `UUID documentId`
   - `logAuditEvent(UUID documentId, ...)`
   - `getAuditTrail(UUID documentId)`

3. **ProcessingService.java** - Updated to use `UUID documentId`
   - `startProcessing(UUID documentId)`
   - `processDocument(UUID documentId)`

4. **TelemetryService.java** - Updated all document ID parameters to `UUID`
   - `startTiming(UUID documentId, String component)`
   - `endTiming(UUID documentId, String component, TelemetryStatus status)`
   - `getTimingsForDocument(UUID documentId)`
   - `getTimingsForComponent(UUID documentId, String component)`
   - `getProcessingStats(UUID documentId)`
   - `recordError(UUID documentId, String component, String errorMessage)`

5. **CallbackService.java** - Updated document ID parameters to `UUID`
   - `createCallback(UUID documentId, ...)`
   - `getCallbacksForDocument(UUID documentId, CallbackStatus statusFilter)`
   - `getCallbackStats(UUID documentId)`

6. **VerificationService.java** - Updated document ID parameters to `UUID`
   - `createVerification(UUID documentId, ...)`
   - `updateVerificationStatus(Long verificationId, UUID documentId, ...)`
   - `getVerificationsForDocument(UUID documentId, VerificationStatus statusFilter)`
   - `getLatestVerification(UUID documentId)`
   - `addNotes(Long verificationId, UUID documentId, String notes)`
   - `createBatchVerifications(UUID documentId, List<DocumentVerification> verifications)`

### Database Migration
- **V1.0.3__migrate_document_id_to_uuid.sql** - Creates the migration to:
  - Drop foreign key constraints from dependent tables
  - Rename existing `id` column to `id_old`
  - Add new `id` column as UUID with `gen_random_uuid()` default
  - Set as PRIMARY KEY
  - Update all dependent tables (audit_events, travel_assignments, processing_results, verifications, callbacks, telemetry_timings) to use UUID
  - Recreate all indexes

## Database Changes

### Column Type Changes
- `ocr_documents.id`: `BIGSERIAL` → `UUID`
- `ocr_documents.processing_result_id`: `BIGINT` → `UUID`
- All `document_id` columns in dependent tables: `BIGINT` → `UUID`

### Foreign Key Constraints
All foreign key constraints are updated to reference the new UUID primary key:
- `ocr_audit_events.document_id` → `ocr_documents.id`
- `ocr_travel_assignments.document_id` → `ocr_documents.id`
- `ocr_document_processing_results.document_id` → `ocr_documents.id`
- `ocr_document_verifications.document_id` → `ocr_documents.id`
- `ocr_extraction_callbacks.document_id` → `ocr_documents.id`
- `ocr_telemetry_timings.document_id` → `ocr_documents.id`

## Breaking Changes for API Clients

### ID Format
- **Before**: `/api/v1/documents/123`
- **After**: `/api/v1/documents/550e8400-e29b-41d4-a716-446655440000`

### Response JSON
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",  // UUID format, not numeric
  "filename": "document.pdf",
  ...
}
```

### Request Parameters
All document ID parameters in URLs and request bodies now expect UUID format instead of numeric IDs.

## Migration Path

1. **Database**: Run Flyway migration V1.0.3
2. **Code**: Deploy updated application (all changes are backward-compatible in terms of compilation)
3. **API Clients**: Update all code that references document IDs to use UUID format instead of numeric IDs

## Compatibility Notes

- **Existing Data**: The migration generates new UUIDs for all existing documents
- **Testing**: All database migrations will run fresh with UUID schema on test/dev environments
- **Production**: Requires data migration plan (existing documents will receive auto-generated UUIDs)

## Testing Checklist

- [ ] Build project: `mvn clean compile`
- [ ] Verify no compilation errors
- [ ] Check that UUID generation works in Document entity
- [ ] Confirm database migrations apply successfully
- [ ] Test API endpoints with UUID document IDs
- [ ] Verify all audit trails and related records use UUID documentId
- [ ] Test callback creation/retrieval with UUID
- [ ] Verify telemetry timing records use UUID

## Performance Impact

UUIDs have minimal performance impact:
- Slightly larger storage requirement (16 bytes vs 8 bytes for Long)
- Index size increases slightly
- Query performance remains similar (UUID is standard indexed type)
- No runtime performance degradation

## Future Considerations

- This UUID pattern can be applied to other entities (User, Callback, etc.) if needed
- Consider UUID for processing result IDs and other resource IDs in future
- Document this security improvement in API documentation
