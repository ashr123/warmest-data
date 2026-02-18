# WarmestData - Quick Start Guide

## 🚀 Quick Deploy

### Local Development (In-Memory)
```bash
./gradlew bootRun
```
Access at: http://localhost:8080

### Local with Redis
```bash
# Start Redis
docker-compose up -d

# Run application
SPRING_PROFILES_ACTIVE=redis ./gradlew bootRun
```
Access at: http://localhost:8080

### Production (3 Instances + Redis)
```bash
# Build JAR
./gradlew bootJar

# Build Docker image
docker build -t warmest-data .

# Deploy
docker-compose -f compose-multi.yaml up

# Access instances
# http://localhost:8080
# http://localhost:8081
# http://localhost:8082
```

## 📝 API Quick Reference

| Method | Endpoint      | Body | Response                 |
|--------|---------------|------|--------------------------|
| PUT    | `/data/{key}` | `42` | Previous value or `null` |
| GET    | `/data/{key}` | -    | Value or 404             |
| DELETE | `/data/{key}` | -    | Previous value or `null` |
| GET    | `/warmest`    | -    | Warmest key or `null`    |

## 🧪 Test

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests WarmestDataStructureTest
```

## 🔍 Verify

```bash
# Test PUT
curl -X PUT http://localhost:8080/data/temp -H "Content-Type: application/json" -d "42"

# Test GET
curl http://localhost:8080/data/temp

# Test GET warmest
curl http://localhost:8080/warmest

# Test DELETE
curl -X DELETE http://localhost:8080/data/temp
```

## 📊 Status

- ✅ Build: SUCCESS
- ✅ Tests: 29/29 passing
- ✅ Docker: Ready
- ✅ Multi-instance: Ready
