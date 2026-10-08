# OCR API

A REST API for document processing that orchestrates OCR extraction and AI-powered document classification. Built on Quarkus 3.9.4 LTS and PostgreSQL 18, this service provides a complete workflow for ingesting documents, extracting text content, classifying document types, and maintaining comprehensive audit trails.

The API is designed to be part of the Tettyrs document processing pipeline, working alongside [ocr-engine](https://github.com/tettyrs-org/ocr-engine) (Python-based text extraction) and [ms-ocr](https://github.com/tettyrs-org/ms-ocr) (Java correction engine) to provide end-to-end document intelligence.

## Features

**Document Lifecycle Management**
- Upload documents with metadata (type, source, tags)
- Full CRUD operations with pagination support
- Document status tracking (PENDING, PROCESSING, COMPLETED, FAILED)
- Persistent file storage in MinIO with secure access

**Asynchronous Processing Pipeline**
- Non-blocking OCR extraction using CompletableFuture workers
- LLM-powered document classification into categories (IDENTITY, FINANCIAL, LEGAL, GENERAL)
- Real-time processing status polling via dedicated endpoints
- Configurable confidence thresholds for both OCR and classification results

**Security & Authentication**
- JWT-based authentication with 24-hour token validity
- Manual HMAC-SHA256 signing (no framework dependencies for simpler maintenance)
- Role-based access control on endpoints
- Stateless token validation without Redis or session storage

**Operational Excellence**
- Detailed audit logs capturing who did what, when, and why
- Flyway-managed migrations for version-controlled schema evolution
- Comprehensive error handling with meaningful error messages
- Integration with travel/assignment workflows for team collaboration

## Architecture & Design

This API is built on a pragmatic architecture that prioritizes simplicity and operational reliability over over-engineered solutions.

**Why Quarkus?** We chose Quarkus 3.9.4 LTS for its exceptional startup time (under 1 second), low memory footprint, and excellent support for containerized deployments. The declarative CDI dependency injection keeps business logic clean and testable.

**Why Manual JWT?** Instead of fighting framework configuration (smallrye-jwt), we implemented straightforward HMAC-SHA256 signing in the TokenService. This gives us complete control, easier debugging, and zero external dependencies for authentication logic.

**Why CompletableFuture for async work?** The processing service uses Java's built-in async framework rather than adding Redis queues or Kafka. For document processing workflows with modest scale, this keeps operations simple and reduces infrastructure complexity.

**Why Panache ORM?** Hibernate Panache provides elegant entity mapping with snake_case database columns while keeping code readable. The active record pattern minimizes boilerplate without sacrificing type safety.

## Tech Stack

- **Framework**: Quarkus 3.9.4 LTS (lightweight, fast startup)
- **Database**: PostgreSQL 18 with Hibernate Panache ORM
- **Object Storage**: MinIO (S3 SDK 2.25.0 for file persistence)
- **Authentication**: Custom JWT with HMAC-SHA256 signing
- **Testing**: JUnit 5, REST Assured, and Mockito
- **Build**: Maven 3.x with Flyway for migrations
- **API Style**: RESTEasy Reactive (non-blocking where applicable)

## Prerequisites

Make sure you have the following installed:

- Java 17 or later (OpenJDK or Temurin recommended)
- PostgreSQL 18 (or compatible version)
- Maven 3.8.1+
- MinIO server running locally or S3 credentials configured

## Getting Started

### 1. Create the Database

Start PostgreSQL and create the application database:

```bash
# Using psql
psql -U postgres
CREATE DATABASE ocr_db;
```

The schema migrations will run automatically when the application starts, so you don't need to create tables manually.

### 2. Configure Your Environment

Create `src/main/resources/application-dev.yaml` with your local settings. This file is in `.gitignore` to keep secrets out of version control:

```yaml
quarkus:
  datasource:
    jdbc:
      url: jdbc:postgresql://localhost:5432/ocr_db
    username: postgres
    password: ${DB_PASSWORD}  # Set this from environment or .env
  
  s3:
    endpoint-override: http://localhost:9000  # MinIO endpoint
    aws:
      region: us-east-1
      credentials:
        type: static
        static-provider:
          access-key-id: ${MINIO_ACCESS_KEY}
          secret-access-key: ${MINIO_SECRET_KEY}
    path-style-access: true
```

Replace the environment variable placeholders with actual values:
- `DB_PASSWORD`: Your PostgreSQL password
- `MINIO_ACCESS_KEY`: Your MinIO access key (default: use your MinIO credentials for local dev)
- `MINIO_SECRET_KEY`: Your MinIO secret key (default: use your MinIO credentials for local dev)

Set these in a local `.env` file (which is in `.gitignore`) or pass them as environment variables when running the application. Never commit actual credentials to the repository.

If you're using actual AWS S3 instead of MinIO, adjust the endpoint and credentials accordingly.

### 3. Start the Development Server

Running in Quarkus dev mode gives you hot reload when you change code:

```bash
./mvnw quarkus:dev
```

The API will be available at `http://localhost:8080`. Try the health check endpoint:

```bash
curl http://localhost:8080/q/health
```

## API Usage

All endpoints require authentication except the login endpoint. The API uses bearer token authentication—get a token first, then include it in the Authorization header for subsequent requests.

### Getting a Token

The login endpoint doesn't require authentication. In development, any email/password combination is accepted (in production, this would connect to your identity provider):

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"***REMOVED***"}'
```

You'll receive a JWT token valid for 24 hours:

```json
{
  "token": "<your-jwt-token>",
  "expires_in": 86400,
  "token_type": "Bearer"
}
```

Save this token and pass it with each request. For convenience, save it as an environment variable:

```bash
TOKEN="<your-jwt-token-here>"
```

**Never commit tokens to version control.** Keep them in environment variables or a local `.env` file (which is in `.gitignore`).

### Document Management

**Upload a new document**

When you upload, the document is created with PENDING status. You'll need to trigger processing separately if you want OCR extraction:

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@invoice.pdf" \
  -F "documentType=INVOICE"
```

The response includes the document ID needed for subsequent operations.

**List all documents you've uploaded**

```bash
curl -X GET http://localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN"
```

Returns paginated results with document metadata.

**Get details for a specific document**

```bash
curl -X GET http://localhost:8080/api/documents/42 \
  -H "Authorization: Bearer $TOKEN"
```

### Document Processing (OCR & Classification)

**Trigger async processing**

This starts the OCR extraction and classification workflow. The endpoint returns immediately with 202 Accepted—processing happens in the background:

```bash
curl -X POST http://localhost:8080/api/documents/42/process \
  -H "Authorization: Bearer $TOKEN"
```

The processing service will:
1. Extract text using the OCR service (takes ~500ms)
2. Classify the document type using the LLM service (takes ~300ms)
3. Store results and update the document status
4. Create audit events for the processing lifecycle

**Check processing results**

Poll this endpoint to see when processing is complete:

```bash
curl -X GET http://localhost:8080/api/documents/42/processing-status \
  -H "Authorization: Bearer $TOKEN"
```

When complete, the response includes:

```json
{
  "ocrText": "Invoice #INV-2026-001\nDate: 2026-10-01\n...",
  "ocrConfidence": 0.95,
  "classificationCategory": "FINANCIAL",
  "classificationConfidence": 0.92,
  "summary": "Document classified as FINANCIAL. Contains 1247 characters of text content.",
  "processingStatus": "COMPLETED",
  "completedAt": "2026-10-01T17:30:45"
}
```

If processing fails, `processingStatus` will be FAILED and `errorMessage` will explain why.

## Project Structure

```
src/main/java/org/tettyrs/
├── api/               # REST controllers and filters
├── config/            # CDI producers (S3Client, etc)
├── dto/               # Data Transfer Objects
├── entities/          # JPA entities with Panache
├── service/           # Business logic
│   ├── ProcessingService    # Async OCR/LLM orchestration
│   ├── OcrService           # Mock OCR extraction
│   ├── LlmService           # Mock document classification
│   └── ...
└── entities/enums/    # Enum types

src/main/resources/
├── application.yaml   # Base configuration
├── application-dev.yaml
├── application-prod.yaml
├── application-test.yaml
└── db/migration/      # Flyway SQL migrations

src/test/java/org/tettyrs/
├── api/
│   ├── AuthControllerTest
│   └── AuthControllerTestProfile
└── service/
    └── ProcessingServiceTest
```

## Testing

Run the full test suite:

```bash
./mvnw test
```

We use a pragmatic testing approach: integration tests against a real (test) database rather than mocked repositories. This catches real problems like query bugs and constraint violations that unit mocks would hide.

**Test Coverage**

- `AuthControllerTest`: JWT generation, token validation, authentication enforcement
- `ProcessingServiceTest`: Async document processing and result persistence

The tests use Quarkus test containers to spin up PostgreSQL for the test database. This ensures tests run against the actual database constraints and migration scripts.

## Authentication & Security

The API uses JWT (JSON Web Tokens) with HMAC-SHA256 signing. This approach is stateless—we don't need Redis or a database lookup to validate tokens.

**How it works:**
- TokenService generates tokens by signing `header.payload` with a secret key using HMAC-SHA256
- JwtValidator verifies tokens by regenerating the signature and comparing it to the provided one
- If signatures match, the token is valid; if not, access is denied with 401 Unauthorized

**Token Details:**
- Validity: 24 hours from issuance
- Signing: HMAC-SHA256 with a configurable secret key
- Format: `header.payload.signature` (three base64-encoded parts separated by dots)
- Encoding: All parts are base64-url encoded

**In Development:**
The login endpoint accepts any email/password combination. In production, you'd replace this with calls to your identity provider (LDAP, OAuth2, etc.).

**Token Rotation:**
Tokens don't refresh automatically. Once expired, users must call login again. For long-running operations, implement refresh tokens (not currently supported—consider it for future work).

## How Document Processing Works

The workflow is designed to handle documents asynchronously, keeping the API responsive while heavy lifting happens in the background.

**Step 1: Upload**
User uploads a document file with metadata (document type, source). The file is stored in MinIO and a Document record is created in PostgreSQL with status PENDING.

**Step 2: Initiate Processing**
User calls the process endpoint. This immediately returns 202 Accepted and queues the work.

**Step 3: Background Processing**
The ProcessingService runs in a background thread (CompletableFuture worker):
- Calls OcrService to extract text (currently mocked with 500ms delay; in production this connects to ocr-engine)
- Calls LlmService to classify the extracted text (currently mocked with 300ms delay)
- Stores results in the DocumentProcessingResult table
- Creates audit events marking PROCESSING_COMPLETED or PROCESSING_FAILED

**Step 4: Results Available**
User polls the processing-status endpoint to retrieve results. The response includes extracted text, confidence scores, classification category, and an optional summary.

The entire workflow is transactional—if OCR or classification fails, the database is left in a consistent state with error information for debugging.

In production, this service integrates with the Tettyrs pipeline: documents flow through ocr-engine for initial extraction, ms-ocr for normalization, and this API for orchestration and audit trail management.

## Troubleshooting

**Server won't start / PostgreSQL connection errors**

If you see `org.postgresql.util.PSQLException: Connection refused`:

1. Verify PostgreSQL is running: `pg_isready -h localhost`
2. Check the connection URL in application-dev.yaml matches your setup
3. Verify the database exists: `psql -U postgres -l | grep ocr_db`
4. If you see migration errors, ensure the database is empty before first startup

**S3/MinIO connection issues**

If file uploads fail with S3 errors:

1. Ensure MinIO is running: `curl http://localhost:9000/minio/health/live`
2. Verify endpoint in application-dev.yaml points to your MinIO instance
3. Check AWS credentials (access key, secret key) match MinIO configuration
4. Try uploading a test file via MinIO console to verify connectivity

**401 Unauthorized on protected endpoints**

Even with a valid token, you're getting rejected:

1. Make sure you include the full token: `Authorization: Bearer <entire-token-string>`
2. Check the token hasn't expired (24 hours from login)
3. Get a fresh token by calling login again
4. Verify the Authorization header format—it must be exactly `Bearer ` (with space) followed by the token

**Processing endpoints return 500 errors**

If document processing fails:

1. Check server logs for the actual error message in PROCESSING_FAILED audit events
2. Ensure document file exists in S3 (processing needs to read the file)
3. Try a smaller file first to rule out size-related issues
4. Check ProcessingService logs—OCR or LLM service might be throwing exceptions

**Tests fail with port already in use**

If `./mvnw test` fails with "port already in use":

1. Kill existing Quarkus processes: `killall java` (or use Task Manager on Windows)
2. Ensure no other service is using ports 8081 or 5432
3. Try again: `./mvnw clean test`

## Development

### IDE Setup

We recommend using IntelliJ IDEA (community edition is fine) or VS Code with the Quarkus extensions.

**In IntelliJ:**
1. Open the project folder
2. Enable annotation processing: Settings → Compiler → Annotation Processors → Enable
3. Mark `src/main/resources` as a Resources folder
4. Configure the Quarkus run configuration for hot reload during development

### Code Style

We follow standard Java conventions with a few house rules:

1. Keep methods focused—if it needs more than 10 lines, consider extracting helper methods
2. Use meaningful variable names (no single-letter variables except in loops)
3. Prefer explicit over clever (readable code > concise code)
4. Document the "why" in comments, not the "what" (the code already shows what it does)
5. Test edge cases, not just the happy path

### Making Changes

1. Create a feature branch from master: `git checkout -b feature/your-feature-name`
2. Make your changes
3. Run tests to ensure nothing breaks: `./mvnw test`
4. Commit with a clear message explaining what and why
5. Push and create a pull request

### Common Development Tasks

**Add a new API endpoint**
1. Create a new method in the appropriate controller (e.g., DocumentController)
2. Add the `@GET`, `@POST`, etc. annotation with the path
3. Inject dependencies with `@Inject`
4. Validate inputs and call service layer
5. Add tests in the corresponding test class

**Add a new database entity**
1. Create a new class in `entities/` extending PanacheEntity
2. Add JPA annotations (@Entity, @Table, @Column)
3. Create a migration file in `db/migration/` to create the table
4. Add a service class to handle business logic
5. Add tests

**Run with production configuration**
```bash
./mvnw quarkus:dev -Dquarkus.profile=prod
```

## Architecture Notes

**Why no Spring Boot?**
We chose Quarkus specifically for its fast startup and low memory usage. Spring is heavier and better suited for traditional monoliths. Our microservice philosophy favors lean, focused services.

**Why CompletableFuture instead of reactive?**
While Quarkus supports fully reactive pipelines, we found that CompletableFuture provides enough async capability for our use case without the cognitive overhead of learning Reactive Streams. It's also easier to debug when things go wrong.

**Why manual JWT instead of a library?**
Libraries like smallrye-jwt have their place, but for simple HMAC-SHA256, the extra configuration complexity wasn't worth it. Our 50-line TokenService is easier to understand and maintain than fighting library quirks.

## License

Proprietary - Tettyrs Organization

## Docker Deployment

### Full Stack (Recommended)

Run the entire OCR stack from the project root:

```bash
cd /path/to/Projects/OCR

# Start all 6 services
docker-compose --env-file .env up -d

# Check service health
docker ps -a --format "table {{.Names}}\t{{.Status}}"
```

This starts the complete microservices architecture:
- **ocr-api** (REST API) - http://localhost:8081
- **ocr-engine** (Python OCR service) - http://localhost:8000
- **ms-ocr** (Java correction engine) - http://localhost:8080
- **ocr-postgres** (Database) - localhost:5432
- **ocr-redis** (Cache) - localhost:6379
- **ocr-minio** (S3 storage) - http://localhost:9000 (API) / http://localhost:9001 (Console)

### Environment Setup

The `.env` file at project root contains shared credentials (gitignored):

```ini
# Database (PostgreSQL) - configure your own values
DB_NAME=${DB_NAME}
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}

# MinIO / S3 - use your credentials
MINIO_ROOT_USER=${MINIO_ROOT_USER}
MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}
AWS_REGION=us-east-1
S3_BUCKET=${S3_BUCKET}

# Redis
REDIS_HOST=redis
REDIS_PORT=6379
```

**Never commit `.env` with actual secrets to git.** See `.env` file for setup instructions.

### API Access

Test the API is running:

```bash
# Check health
curl http://localhost:8081/q/health

# Login to get token
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"***REMOVED***"}'

# Upload document with token
TOKEN="<your-jwt-token>"
curl -X POST http://localhost:8081/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@document.pdf"
```

### MinIO Console

Access S3 storage at:
- **URL**: http://localhost:9001
- **Username**: ${MINIO_ROOT_USER}
- **Password**: ${MINIO_ROOT_PASSWORD}

Create the `ocr-documents` bucket for file storage.

### Stopping the Stack

```bash
# Stop all services
docker-compose down

# Remove volumes (full cleanup)
docker-compose down -v
```

### Troubleshooting Docker

**Services not starting?**
```bash
# Check logs
docker logs ocr-api
docker logs ocr-postgres

# Verify environment variables
docker exec ocr-postgres printenv | grep DB_
```

**Database connection errors?**
```bash
# Verify database and user exist
docker exec ocr-postgres psql -U postgres -l

# Create app user if missing (replace ${DB_PASSWORD} with your actual password)
docker exec ocr-postgres psql -U postgres -c \
  "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASSWORD}';"
docker exec ocr-postgres psql -U postgres -c \
  "GRANT ALL PRIVILEGES ON DATABASE ocr_results TO ${DB_USER};"
```

**Ports already in use?**
```bash
# Kill existing containers
docker-compose down

# Or change ports in docker-compose.yml
```

## Contributing

This project is maintained by the Tettyrs Organization. See [@tettyrs](https://github.com/tettyrs) for the maintainer profile.

## Support & Questions

For issues or questions:
1. Check the Troubleshooting section above
2. Review existing GitHub issues
3. Create a new issue with a clear description and steps to reproduce
4. Reach out to the Tettyrs team for architecture discussions
