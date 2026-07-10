# Distributed URL Shortener with Redis Caching & Rate Limiting

A production-style URL shortener built to demonstrate core backend system design concepts: caching strategies, distributed rate limiting, database indexing, and containerized deployment — backed by real load testing data rather than assumed performance.

## Overview

This system takes a long URL and generates a short, unique code that redirects back to the original URL. Beyond the basic CRUD functionality, it implements the caching and traffic-control patterns used in real-world, high-traffic systems (similar to bit.ly / TinyURL).

## Architecture

```
Client
  │
  ▼
Rate Limiter (Redis-backed, token counter per IP)
  │
  ▼
Spring Boot REST API
  │
  ├──► Redis (cache-aside: checked first on reads)
  │
  └──► PostgreSQL (persistent storage, indexed on short_code)
```

**Write path (`POST /shorten`):**
1. Request generates a new row in PostgreSQL
2. Auto-incremented DB ID is Base62-encoded into a short code (e.g., `125` → `"cb"`)
3. Short code is saved back to the same row

**Read path (`GET /{code}`):**
1. Check Redis for the short code
2. **Cache hit** → return immediately, no DB query
3. **Cache miss** → query PostgreSQL, populate Redis (24hr TTL), then return

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.1, Java 17 |
| Cache | Redis 7 |
| Database | PostgreSQL 16 |
| Rate Limiting | Redis atomic counters (token bucket pattern) |
| Load Testing | k6 |
| Deployment | Docker Compose |

## Key Features

### 1. Cache-Aside Caching
Redirect lookups check Redis before touching PostgreSQL, reducing database load for repeated lookups. Cache entries expire after 24 hours (TTL) to avoid unbounded growth.

### 2. Redis-Backed Rate Limiting
Each client (identified by IP) is limited to 100 requests per 60-second window. Implemented using Redis `INCR` + `EXPIRE`, which guarantees atomic, race-condition-free counting even under concurrent requests. Requests exceeding the limit receive an HTTP `429 Too Many Requests` response.

### 3. Collision-Free Short Code Generation
Short codes are generated via Base62 encoding of the database's auto-incremented primary key, guaranteeing uniqueness by construction rather than relying on random generation + collision checks.

### 4. Containerized Deployment
The entire stack (API, PostgreSQL, Redis) runs via a single `docker-compose up` command, with services communicating over Docker's internal network.

## Load Testing Results (k6)

All numbers below were generated using k6 against the running system — not estimated.

**Performance test** (50 concurrent virtual users, 30 seconds, rate limiter disabled to isolate raw throughput):
| Metric | Result |
|---|---|
| Total requests | 2,775 |
| Throughput | ~85 requests/sec |
| Error rate | 0% |
| Avg response time | 328 ms |
| p95 latency | 579 ms |

**Rate limiter validation test** (1 virtual user, 110 sequential requests, limit set to 100/60s):
| Metric | Result |
|---|---|
| Requests allowed | 100 |
| Requests blocked (429) | 10 |
| Blocking accuracy | 100% — limit enforced exactly as configured |

## API Endpoints

### Shorten a URL
```
POST /shorten
Content-Type: application/json

{
  "url": "https://example.com/some/long/path"
}
```
**Response:**
```
http://localhost:8080/cb
```

### Redirect to original URL
```
GET /{shortCode}
```
Returns an HTTP 302 redirect to the original long URL.

## Running Locally

**Prerequisites:** Docker Desktop installed and running.

```bash
git clone https://github.com/rushikeshkokate150/distributed-url-shortener.git
cd distributed-url-shortener
docker-compose up --build
```

The API will be available at `http://localhost:8080`.

## Cloud Deployment (AWS)

Beyond local Docker Compose, this project is deployed on real AWS infrastructure using managed services rather than self-hosted containers for the database and cache:

| Component | Service |
|---|---|
| Application host | EC2 (t3.micro), running the Dockerized Spring Boot app |
| Database | RDS (PostgreSQL, db.t4g.micro) |
| Cache | ElastiCache (Redis OSS, cache.t4g.micro), TLS-encrypted in transit |

**Architecture change for cloud deployment:** the app's `application.properties` swaps local container hostnames (`postgres`, `redis`) for the actual RDS endpoint and ElastiCache configuration endpoint, with security groups configured to allow only the EC2 instance to reach each managed service.

### Debugging notes: a real production issue

Deploying to ElastiCache surfaced two compounding issues that don't show up in local development, worth documenting since they reflect genuine debugging rather than following a fixed recipe:

1. **Netty's async DNS resolver failed silently inside the container**, timing out after a full minute with an `Unable to connect .../<unresolved>` error — despite the same hostname resolving instantly via `nslookup` and `nc` on the host itself. Root cause: Lettuce (the Redis client) defaults to Netty's DNS resolver, which behaves inconsistently in containerized environments for certain AWS-internal hostnames.
2. **Switching to the raw IP address to sidestep the DNS issue then broke TLS certificate validation** (`No subject alternative names matching IP address... found`), since ElastiCache's certificate is issued for the hostname, not the IP — the two fixes were mutually exclusive on the surface.

**Resolution:** kept the hostname (required for valid TLS), and instead explicitly configured Lettuce's `ClientResources` to use the JVM's built-in DNS resolver instead of Netty's:

```java
@Bean(destroyMethod = "shutdown")
public ClientResources lettuceClientResources() {
    return DefaultClientResources.builder()
            .dnsResolver(DnsResolvers.JVM_DEFAULT)
            .build();
}
```

This preserved TLS validation while avoiding the resolver bug entirely — diagnosed by isolating each variable independently (DNS resolution vs. TCP connectivity vs. TLS handshake) rather than guessing at the fix.

## Running Load Tests

Install [k6](https://k6.io/docs/getting-started/installation/), then:

```bash
k6 run loadtest.js          # throughput/latency test
k6 run ratelimit_test.js    # rate limiter validation test
```

## Design Notes & Tradeoffs

- **Rate limiting is per-IP**, which is simple and effective for this scope but would need to move to a per-API-key model in a multi-tenant production system, since multiple users behind the same NAT/IP would share a limit.
- **Cache invalidation** is TTL-based (24hr expiry) rather than event-driven, prioritizing simplicity over strict cache consistency — acceptable for a URL shortener where long URLs rarely change once created.
- **Database indexing**: `short_code` has a unique index, making redirect lookups (on cache miss) efficient even as the table grows.

## Future Improvements

- Click-count analytics per short URL
- Custom/vanity short codes
- Horizontal scaling test (multiple app instances behind a load balancer)
- Migrate rate limiting to a Lua script for stronger atomicity guarantees under extreme concurrency
