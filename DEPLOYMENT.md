# Deployment Guide

## Local Development (Docker Compose)

### Setup
```bash
# Copy environment template
cp .env.example .env

# Edit .env dengan development values
nano .env

# Start services
docker-compose up -d

# Check logs
docker-compose logs -f api
```

### Environment Variables
See `.env.example` for all available variables.

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
