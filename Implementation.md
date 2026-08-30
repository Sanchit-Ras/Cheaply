# Cheaply V2 - Spring Boot Implementation Plan

## Project Vision

Cheaply V2 is a production-style grocery price comparison platform that helps users find the cheapest products across multiple online grocery providers by normalizing prices and ranking products based on price-per-unit.

The goal of V2 is **not** to reinvent the product. The goal is to rebuild the backend using modern Spring Boot practices while preserving the core functionality that already works.

The project should demonstrate practical backend engineering skills expected from a backend developer:

- Spring Boot
- REST APIs
- Spring Security
- JWT Authentication
- PostgreSQL
- Redis Caching
- Docker
- Validation
- Exception Handling
- Layered Architecture
- Async Processing
- Testing

The project should remain realistic for a solo developer and avoid unnecessary complexity.

---

# Core Principle

## Keep Scraping Separate

The existing Python scraper already works and contains significant Selenium logic.

Rewriting it in Java would:

- Add development time
- Increase maintenance effort
- Provide little learning value
- Create more opportunities for scraper breakage

Therefore:

```text
Python Scraper
    ↓
Spring Boot Backend
    ↓
Frontend
```

The scraper becomes an external service that Spring Boot consumes.

This allows Spring Boot to focus on business logic instead of browser automation.

---

# High-Level Architecture

```text
Frontend

    ↓

Spring Boot Backend

    ├── Authentication Module
    ├── Search Module
    ├── Product Ranking Engine
    ├── Search History Module
    ├── Redis Cache Module
    ├── Scraper Integration Module
    └── Security Module

    ↓

Python Scraper API

    ↓

Amazon
JioMart
Flipkart
```

---

# Technology Stack

## Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- Spring Validation
- Lombok
- PostgreSQL
- Redis
- JWT

## Database

PostgreSQL

Reason:

- Better alignment with JPA/Hibernate learning
- Strong industry adoption
- Relational data fits this project naturally

## Cache

Redis

Reason:

- Purpose-built for temporary caching
- Faster than database-based caching
- Ideal for search result reuse

## Documentation

- Swagger / OpenAPI

## Containerization

- Docker
- Docker Compose

---

# Project Structure

```text
com.cheaply

├── auth
├── search
├── product
├── history
├── scraper
├── cache
├── security
├── common
├── config
├── exception
└── util
```

---

# Module 1 - Authentication

## Features

### Signup

```http
POST /api/auth/signup
```

### Login

```http
POST /api/auth/login
```

### Refresh Token

```http
POST /api/auth/refresh
```

---

## Security

Use:

- Spring Security
- JWT Access Token
- JWT Refresh Token
- BCrypt Password Encoding

---

## Roles

```text
USER
ADMIN
```

Only USER functionality is required initially.

ADMIN exists for future expansion.

---

# Module 2 - User Management

## Entity

### User

```text
id
username
email
password
role
createdAt
updatedAt
```

---

# Module 3 - Search History

## Purpose

Store recent searches for authenticated users.

This feature already exists in V1 and should be retained.

---

## Entity

### SearchHistory

```text
id
query
searchedAt
userId
```

---

## Rules

- Store only the latest 20 searches
- Old searches are automatically removed
- Duplicate searches can be updated instead of inserted

---

## APIs

```http
GET /api/history
```

Returns recent searches.

---

# Module 4 - Scraper Integration

## Approach

The Python scraper becomes an independent API service.

Spring Boot communicates through HTTP.

---

## Example

### Request

```http
POST /scrape
```

```json
{
  "query": "rice"
}
```

### Response

```json
[
  {
    "title": "...",
    "price": 100,
    "source": "Amazon"
  }
]
```

---

## Spring Boot Component

### ScraperClient

Responsibilities:

- Call scraper API
- Handle failures
- Handle timeouts
- Convert scraper response into DTOs

---

## Future Safety

If scraping logic changes:

```text
Only Python Service Changes

Spring Boot Remains Untouched
```

---

# Module 5 - Product Normalization Engine

This is the heart of Cheaply.

The existing algorithm should be migrated from Python into Java.

---

## Responsibilities

Extract:

```text
500g
1kg
2kg

500ml
1L
2L
```

Convert to standard units.

Calculate:

```text
Price Per KG
Price Per Liter
```

---

## Example

Input:

```text
Aashirvaad Atta 5kg
₹300
```

Output:

```text
₹60 per kg
```

---

## Service

```java
PriceNormalizationService
```

---

# Module 6 - Product Ranking Engine

## Responsibilities

- Merge products from all providers
- Normalize prices
- Rank globally
- Return cheapest products first

---

## Service

```java
RankingService
```

---

## Output

```text
1. JioMart
2. Amazon
3. Flipkart
```

Based on normalized price.

---

# Module 7 - Redis Caching

## Objective

Avoid unnecessary scraping.

---

## Flow

```text
User Search

    ↓

Check Redis

    ↓

Cache Hit
    ↓
Return Data

Cache Miss
    ↓
Call Scraper
    ↓
Store Result
    ↓
Return Data
```

---

## Cache TTL

```text
15 Minutes
```

Same behavior as V1.

---

## Why Not Store Results Permanently?

Cheaply depends on live pricing.

Permanent storage creates:

- Stale prices
- Incorrect rankings
- User trust issues

Therefore:

```text
Cache Only
No Long-Term Product Storage
```

---

# Module 8 - Search API

## Search Endpoint

```http
POST /api/search
```

---

## Request

```json
{
  "query": "rice"
}
```

---

## Flow

```text
Validate Query

↓

Check Redis

↓

Cache Hit?
  Yes → Return

↓

Call Scraper

↓

Normalize Prices

↓

Rank Products

↓

Save Search History

↓

Cache Results

↓

Return Response
```

---

# Module 9 - Validation

Use Bean Validation.

Examples:

```java
@NotBlank
@Email
@Size
```

Validation belongs in DTOs.

Controllers remain clean.

---

# Module 10 - Exception Handling

Create custom exceptions.

Examples:

```java
UserAlreadyExistsException
InvalidCredentialsException
ScraperUnavailableException
InvalidSearchQueryException
```

---

## Global Exception Handler

Single response structure.

```json
{
  "success": false,
  "message": "Error message"
}
```

---

# Module 11 - Logging

Log important business events.

Examples:

```text
User Registration

User Login

Search Started

Cache Hit

Cache Miss

Scraper Call

Search Completed

Error Events
```

Use:

```java
SLF4J
```

No ELK stack.

No distributed tracing.

Keep it simple.

---

# Module 12 - Testing

## Unit Tests

Focus on:

- JWT Service
- Price Normalization
- Ranking Logic

---

## Integration Tests

Focus on:

- Authentication Flow
- Search Flow

Avoid chasing coverage percentages.

Test critical business logic.

---

# Module 13 - Frontend Integration

The existing frontend already contains:

- Authentication screens
- Search UI
- Search history
- Product results
- Styling and animations

The frontend should be retained and updated to consume Spring Boot APIs instead of Flask routes.

The goal is migration, not redesign.

---

# Module 14 - Dockerization

## Containers

```text
Spring Boot
PostgreSQL
Redis
Python Scraper
```

---

## Docker Compose

```text
docker-compose.yml
```

Single command:

```bash
docker compose up
```

should bring up the complete system.

---

# Features Explicitly Excluded

The following technologies are intentionally excluded:

- Microservices
- Kafka
- RabbitMQ
- Kubernetes
- ElasticSearch
- API Gateway
- Event Sourcing
- CQRS
- GraphQL
- AWS Infrastructure

Reason:

They add significant complexity while providing little value for the project's goals.

---

# Expected Learning Outcomes

By completing Cheaply V2, the project should demonstrate:

- Spring Boot fundamentals
- REST API development
- Spring Security
- JWT Authentication
- PostgreSQL + JPA
- Redis Caching
- Docker
- DTO Design
- Validation
- Exception Handling
- Layered Architecture
- External Service Integration
- Unit Testing
- Integration Testing

This is sufficient for a strong backend portfolio project without becoming unmanageable for a solo developer.