# Deployment Guide

## Local Development (Docker Compose)

### Full Stack Setup (Recommended)

The project includes a complete docker-compose.yml at the root that orchestrates 6 services:

```bash
cd /path/to/Projects/OCR

# Start complete stack
docker-compose --env-file .env up -d

# Verify all services are healthy
docker ps -a --format "table {{.Names}}\t{{.Status}}"

# Expected output:
# NAMES          STATUS
# ocr-api        Up X minutes (healthy)
# ms-ocr         Up X minutes (healthy)
# ocr-redis      Up X minutes (healthy)
# ocr-minio      Up X minutes (healthy)
# ocr-engine     Up X minutes (healthy)
# ocr-postgres   Up X minutes (healthy)
```

### Environment Configuration

The `.env` file at project root is required:

```bash
# Copy from template if not exists
cp .env .env.backup

# Key variables needed:
DB_NAME=ocr_results                    # PostgreSQL database
DB_USER=                     # App user
DB_PASSWORD=                  # App password
MINIO_ROOT_USER=             # MinIO access
MINIO_ROOT_PASSWORD=         # MinIO secret
```

**IMPORTANT**: Never commit `.env` to git. Use `.env` template only for examples.

### Service Details

| Service | Port | Protocol | Credentials |
|---------|------|----------|-------------|
| **ocr-api** | 8081 | HTTP | JWT token required |
| **ocr-engine** | 8000 | HTTP | No auth |
| **ms-ocr** | 8080 | HTTP | No auth |
| **ocr-postgres** | 5432 | TCP |  /  |
| **ocr-redis** | 6379 | TCP | No password |
| **ocr-minio** | 9000/9001 | HTTP |  /  |

### Initial Database Setup

When postgres container starts, create the app database:

```bash
# Create app user and grant privileges
docker exec ocr-postgres psql -U postgres -c \
  "CREATE USER  WITH PASSWORD '';"
docker exec ocr-postgres psql -U postgres -c \
  "ALTER USER  WITH CREATEDB;"
docker exec ocr-postgres psql -U postgres -d ocr_results -c \
  "GRANT ALL PRIVILEGES ON SCHEMA public TO ;"
docker exec ocr-postgres psql -U postgres -d ocr_results -c \
  "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO ;"
docker exec ocr-postgres psql -U postgres -d ocr_results -c \
  "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO ;"
```

Quarkus migrations (Flyway) will run automatically when ocr-api starts.

### Stopping Services

```bash
# Graceful shutdown (keeps volumes)
docker-compose down

# Full cleanup (removes data)
docker-compose down -v
```

### Logs & Debugging

```bash
# View logs for specific service
docker logs ocr-api -f
docker logs ocr-postgres -f

# See all logs
docker-compose logs -f

# Check container status
docker ps -a
docker inspect ocr-api
```

---

## Production Deployment (Kubernetes)

### Prerequisites
- Kubernetes cluster (1.20+)
- kubectl configured
- Docker image pushed to registry

### Setup Kubernetes Secret

```bash
# 1. Copy secret template
cp secret.yaml.example secret.yaml

# 2. Edit dengan production values
nano secret.yaml

# Generate secure JWT_SECRET:
openssl rand -base64 32

# 3. Apply secret to cluster
kubectl apply -f secret.yaml

# 4. Verify secret created
kubectl get secrets ocr-api-secrets
kubectl describe secret ocr-api-secrets
```

### Deploy Application

```bash
# 1. Copy deployment template
cp deployment.yaml.example deployment.yaml

# 2. Edit dengan environment-specific values
nano deployment.yaml

# 3. Apply deployment
kubectl apply -f deployment.yaml

# 4. Check deployment status
kubectl get deployment ocr-api
kubectl get pods -l app=ocr-api
kubectl logs -l app=ocr-api -f

# 5. Check service
kubectl get service ocr-api
```

### Health Checks

```bash
# Port forward untuk testing
kubectl port-forward svc/ocr-api 8080:8080

# Test endpoints
curl http://localhost:8080/q/health
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready
```

---

## Environment Configuration

### Development (.env)
- See `.env.example` for template
- Use local/test values
- Never commit actual `.env` to git

### Production (Kubernetes Secret)
- See `secret.yaml.example` for template
- Use secure, randomly generated values
- Never commit actual `secret.yaml` to git
- Rotate secrets regularly
- Use secret management tool (HashiCorp Vault, AWS Secrets Manager, etc)

---

## Secrets Management

### Never commit to git:
- `.env` (use `.env.example` template)
- `secret.yaml` (use `secret.yaml.example` template)
- `deployment.yaml` (use `deployment.yaml.example` template)
- Any files containing credentials

### To regenerate JWT secret:
```bash
openssl rand -base64 32
```

Result: use this value in secret.yaml as `jwt-secret`

---

## Troubleshooting

### Pod fails to start
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name>
```

### Secret not found
```bash
kubectl get secrets -n default
kubectl describe secret ocr-api-secrets
```

### Health check failing
```bash
kubectl port-forward svc/ocr-api 8080:8080
curl -v http://localhost:8080/q/health/ready
```

---

## Security Notes

1. **JWT Secret**: Minimum 32 characters, random, unique per environment
2. **Database Password**: Use strong password, 16+ characters
3. **S3 Credentials**: Rotate regularly, use IAM roles when possible
4. **CORS Origins**: Restrict to known domains only
5. **Image Registry**: Use private registry with authentication
6. **Network Policies**: Implement Kubernetes network policies to restrict traffic

---

## Scaling

### Horizontal Scaling
```bash
kubectl scale deployment ocr-api --replicas=3
```

### Resource Limits
Update `deployment.yaml` resources section based on load testing:
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

---

## Monitoring

### Prometheus metrics available at:
```
http://localhost:8080/q/metrics
```

### Health endpoints:
- Liveness: `/q/health/live` - Is pod running?
- Readiness: `/q/health/ready` - Ready to serve requests?
