# Redis Token Management

A stateless authentication and authorization REST API built with **Java
17, Spring Boot 4, Spring Security, JWT, PostgreSQL, and Redis**.
PostgreSQL stores user accounts and roles. Redis stores the currently
active access/refresh token pair and revoked-token markers.

## Contents

-   [Features](#features)
-   [Technology stack](#technology-stack)
-   [Architecture](#architecture)
-   [Prerequisites](#prerequisites)
-   [Run the complete application with Docker
    Compose](#run-the-complete-application-with-docker-compose)
-   [Run Spring Boot locally with Docker
    dependencies](#run-spring-boot-locally-with-docker-dependencies)
-   [Configuration](#configuration)
-   [API reference](#api-reference)
-   [Request and response lifecycle](#request-and-response-lifecycle)
-   [Redis key design](#redis-key-design)
-   [View PostgreSQL data](#view-postgresql-data)
-   [Useful Docker commands](#useful-docker-commands)
-   [Build and test](#build-and-test)
-   [Security and design notes](#security-and-design-notes)
-   [Project structure](#project-structure)

## Features

-   User registration with BCrypt password hashing.
-   Username/password authentication through Spring Security.
-   Short-lived access JWTs and longer-lived refresh JWTs.
-   JWT signature, issuer, expiration, and token-type validation.
-   Redis-backed validation of the currently active access token.
-   Redis-backed storage of the current refresh token.
-   Atomic refresh-token rotation using a Redis Lua script.
-   Logout removes the user's active token pair and creates blacklist
    markers until token expiry.
-   SHA-256 hashes are used as blacklist key suffixes; raw tokens are
    not used as blacklist key names.
-   New users receive `ROLE_USER`.
-   `/api/admin/**` endpoints require `ROLE_ADMIN`.
-   Request validation and centralized API error responses.
-   Docker Compose services for PostgreSQL, Redis, and the Spring Boot
    application.

## Technology stack

  Component             Technology
  --------------------- -------------------------------
  Language              Java 17
  Framework             Spring Boot 4
  Security              Spring Security
  Authentication        JSON Web Tokens (JJWT 0.12.6)
  Relational database   PostgreSQL 16
  Token storage         Redis 7
  Persistence           Spring Data JPA / Hibernate
  Redis integration     Spring Data Redis
  Containerization      Docker and Docker Compose
  Build                 Maven

## Architecture

``` mermaid
flowchart TD
    CLIENT["Client / API consumer"]
    APP["Spring Boot REST API"]
    SEC["Spring Security + JWT filter"]
    AUTH["Controller and service layer"]
    PG[("PostgreSQL<br/>users and roles")]
    REDIS[("Redis<br/>active tokens and revocation markers")]

    CLIENT -->|"HTTP requests + Bearer access token"| APP
    APP --> SEC
    SEC --> AUTH
    AUTH <--> PG
    AUTH <--> REDIS
```

**Responsibilities**

-   **Spring Boot:** exposes the REST API and coordinates application
    services.
-   **Spring Security:** protects endpoints and enforces role-based
    authorization.
-   **JWT provider:** creates and validates signed access and refresh
    tokens.
-   **PostgreSQL:** stores user account information and roles.
-   **Redis:** stores the active token pair per username and blacklist
    markers with expiration.

## Prerequisites

For the fully containerized setup:

-   Docker Desktop or Docker Engine.
-   Docker Compose v2.
-   A terminal (PowerShell, Command Prompt, Bash, or zsh).

For running Spring Boot outside Docker:

-   Java 17 or later.
-   Maven 3.9 or the included Maven wrapper.

## Run the complete application with Docker Compose

The supplied `docker-compose.yaml` defines three services:

  Service      Image/build                             Container port Purpose
  ------------ ------------------------------------- ---------------- -----------------
  `app`        Built from the project `Dockerfile`               8080 Spring Boot API
  `postgres`   `postgres:16-alpine`                              5432 User database
  `redis`      `redis:7-alpine`                                  6379 Token storage

### 1. Open the project directory

Run Docker Compose commands from the project root directory containing
`docker-compose.yaml`.

``` bash
cd Spring-Boot-Redis-Token-Management
```

### 2. Create the environment file

Copy the example file:

**Windows PowerShell**

``` powershell
Copy-Item .env.example .env
```

**macOS / Linux / Git Bash**

``` bash
cp .env.example .env
```

Generate a Base64-encoded JWT secret that decodes to at least 32 bytes:

``` bash
openssl rand -base64 32
```

Put the generated value in `.env`:

``` dotenv
DB_PASSWORD=replace-with-a-local-database-password
JWT_SECRET=replace-with-the-generated-base64-secret
```

Do not commit `.env` to source control. The example database password is
for local development only.

### 3. Build and start all services

``` bash
docker compose up --build -d
```

Compose builds the Spring Boot image from the `Dockerfile`, starts
PostgreSQL and Redis, waits for their health checks, and then starts the
application.

### 4. Check container status

``` bash
docker compose ps
```

Check application logs:

``` bash
docker compose logs -f app
```

The API is available at:

``` text
http://localhost:8080
```

The root endpoint can be used for a basic application status response:

``` http
GET /
```

Example response:

``` json
{
  "application": "Redis Token Management",
  "status": "running"
}
```

### 5. Stop the services

``` bash
docker compose down
```

This stops and removes the containers but preserves the named volumes.

To delete the containers **and permanently delete the PostgreSQL and
Redis volume data**, use:

``` bash
docker compose down -v
```

Use the second command only when you intentionally want to remove the
stored data.

## Run Spring Boot locally with Docker dependencies

This option runs PostgreSQL and Redis in Docker while running Spring
Boot from IntelliJ IDEA or the command line.

Start only the dependencies:

``` bash
docker compose up -d postgres redis
```

Set the environment variables for the local Spring Boot process:

**PowerShell**

``` powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/redis_spring"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="your-local-db-password"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:JWT_SECRET="your-generated-base64-secret"
$env:JWT_ISSUER="redis-token-management"
$env:JWT_ACCESS_EXPIRATION_MS="900000"
$env:JWT_REFRESH_EXPIRATION_MS="604800000"
```

Use the same `DB_PASSWORD` value that was supplied to the PostgreSQL
container.

Run the application:

**Windows**

``` powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux**

``` bash
./mvnw spring-boot:run
```

**Docker networking distinction**

-   Spring Boot running on your host machine connects to PostgreSQL and
    Redis through `localhost`.
-   Spring Boot running inside Docker Compose connects through the
    service names `postgres` and `redis`.

## Configuration

The application's `src/main/resources/application.yaml` reads
configuration from environment variables.

  -------------------------------------------------------------------------------------------------------
  Environment variable          Default / Compose value                           Description
  ----------------------------- ------------------------------------------------- -----------------------
  `DB_URL`                      `jdbc:postgresql://localhost:5432/redis_spring`   JDBC connection URL

  `DB_USERNAME`                 `postgres`                                        PostgreSQL username

  `DB_PASSWORD`                 `postgres` locally; `.env` in Compose             PostgreSQL password

  `REDIS_HOST`                  `localhost` locally; `redis` in Compose           Redis hostname

  `REDIS_PORT`                  `6379`                                            Redis port

  `JWT_SECRET`                  Required                                          Base64 secret; at least
                                                                                  32 decoded bytes

  `JWT_ISSUER`                  `redis-token-management`                          Expected JWT issuer

  `JWT_ACCESS_EXPIRATION_MS`    `900000`                                          Access token lifetime
                                                                                  (15 minutes)

  `JWT_REFRESH_EXPIRATION_MS`   `604800000`                                       Refresh token lifetime
                                                                                  (7 days)

  `DDL_AUTO`                    `update`                                          Hibernate schema
                                                                                  setting
  -------------------------------------------------------------------------------------------------------

The values above are the project's configured defaults. Production
environments should use managed secrets, database migrations, and a
deliberate schema-validation strategy.

## API reference

Base URL:

``` text
http://localhost:8080
```

### Endpoint summary

  ------------------------------------------------------------------------------
  Method           Endpoint               Access                  Success status
  ---------------- ---------------------- ---------------- ---------------------
  `GET`            `/`                    Public                             200

  `POST`           `/api/auth/register`   Public                             201

  `POST`           `/api/auth/login`      Public                             200

  `POST`           `/api/auth/refresh`    Public; valid                      200
                                          refresh token    
                                          required in body 

  `POST`           `/api/auth/logout`     Authenticated                      204
                                          access token     

  `GET`            `/api/users/me`        Authenticated                      200
                                          access token     

  `GET`            `/api/test`            Authenticated                      200
                                          access token     

  `GET`            `/api/admin/users`     `ROLE_ADMIN`                       200
  ------------------------------------------------------------------------------

### 1. Register a user

``` http
POST /api/auth/register
Content-Type: application/json
```

Request:

``` json
{
  "username": "demo-user",
  "password": "a-long-demo-password"
}
```

Validation rules in the request DTO:

-   Username is required and must contain 3 to 80 characters.
-   Password is required and must contain 10 to 72 characters.

A successful registration creates a `ROLE_USER` account and returns an
access/refresh token pair with HTTP `201 Created`.

### 2. Login

``` http
POST /api/auth/login
Content-Type: application/json
```

Request:

``` json
{
  "username": "demo-user",
  "password": "a-long-demo-password"
}
```

A successful login returns HTTP `200 OK` with the token pair. The
application replaces the previous active token pair for that username.

### Authentication response

Registration and login return the `AuthenticationResponse` object. Its
fields are:

``` json
{
  "accessToken": "<access-jwt>",
  "refreshToken": "<refresh-jwt>",
  "tokenType": "Bearer",
  "authorities": [
    {
      "authority": "ROLE_USER"
    }
  ]
}
```

The JWT strings above are placeholders. Use the actual values returned
by the API.

### 3. Call an authenticated endpoint

Send the access token in the Authorization header:

``` http
GET /api/users/me
Authorization: Bearer <access-token>
```

The `/api/test` endpoint can also be used to verify that authentication
succeeds:

``` http
GET /api/test
Authorization: Bearer <access-token>
```

### 4. Refresh tokens

``` http
POST /api/auth/refresh
Content-Type: application/json
```

Request:

``` json
{
  "refreshToken": "<refresh-token>"
}
```

A successful refresh returns a new access token and a new refresh token.
The submitted refresh token is replaced atomically in Redis and should
not be reused.

### 5. Logout

``` http
POST /api/auth/logout
Authorization: Bearer <access-token>
```

A successful logout returns HTTP `204 No Content`. The current access
and refresh tokens are removed from the active-token keys and their
hashes are blacklisted until their respective expiry times.

### 6. Administrator: list users

``` http
GET /api/admin/users
Authorization: Bearer <admin-access-token>
```

Requires `ROLE_ADMIN`. The response contains user IDs, usernames, and
roles. It does not return password hashes.

New registrations always receive `ROLE_USER`. There is no public API for
self-promoting to administrator. A trusted administrator account must be
promoted through a controlled database administration process. For
example, after connecting to the project's database:

``` sql
UPDATE users
SET role = 'ROLE_ADMIN'
WHERE username = 'trusted-admin';
```

The security filter loads user details from PostgreSQL on authenticated
requests, so the updated role is picked up on a subsequent request.
Treat role changes as privileged administrative operations.

## Request and response lifecycle

### Registration and login lifecycle

``` mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as AuthController
    participant Service as AuthService
    participant Security as AuthenticationManager
    participant DB as PostgreSQL
    participant JWT as JwtTokenProvider
    participant Redis

    Client->>Controller: POST /api/auth/register or /login
    Controller->>Service: Validate request and process credentials

    alt Registration
        Service->>DB: Check username and save BCrypt password + ROLE_USER
    end

    Service->>Security: Authenticate username and password
    Security->>DB: Load user and verify password
    DB-->>Security: User details and authorities
    Security-->>Service: Authenticated principal

    Service->>JWT: Generate access JWT and refresh JWT
    JWT-->>Service: Signed token pair
    Service->>Redis: Revoke previous pair and store new pair with TTL
    Redis-->>Service: Token storage complete
    Service-->>Controller: AuthenticationResponse
    Controller-->>Client: 201 Created (register) / 200 OK (login)
```

### Protected API request lifecycle

``` mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Filter as JwtAuthenticationFilter
    participant JWT as JwtTokenProvider
    participant Redis
    participant UserService as CustomUserService
    participant Controller

    Client->>Filter: Request with Authorization: Bearer access-token
    Filter->>JWT: Validate signature, issuer, expiry and access token type
    JWT-->>Filter: Valid / invalid
    Filter->>Redis: Check access-token blacklist
    Redis-->>Filter: Not revoked / revoked
    Filter->>JWT: Read username from token
    Filter->>Redis: Compare token with current active access token
    Redis-->>Filter: Match / no match
    Filter->>UserService: Load current user and authorities
    UserService-->>Filter: UserDetails
    Filter->>Controller: Continue with authenticated SecurityContext
    Controller-->>Client: Protected API response
```

If the token is invalid, expired, revoked, not the current token, or is
a refresh token presented as an access token, the request is not
authenticated. Protected endpoints require authentication;
`/api/admin/**` additionally requires `ROLE_ADMIN`.

### Refresh-token lifecycle

``` mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as AuthController
    participant Service as AuthService
    participant JWT as JwtTokenProvider
    participant Redis

    Client->>Controller: POST /api/auth/refresh with refresh token
    Controller->>Service: refreshToken(refreshToken)
    Service->>JWT: Validate signature, issuer, expiry and refresh type
    JWT-->>Service: Valid / invalid
    Service->>Redis: Check blacklist and current refresh token
    Redis-->>Service: Current token / stale token
    Service->>JWT: Generate new access and refresh tokens
    Service->>Redis: Lua compare-and-set rotation
    alt Submitted token is still current
        Redis-->>Service: Rotation successful
        Service-->>Controller: New AuthenticationResponse
        Controller-->>Client: 200 OK with new token pair
    else Token is stale or already used
        Redis-->>Service: Rotation rejected
        Service-->>Controller: Unauthorized error
        Controller-->>Client: 401 Unauthorized
    end
```

### Logout lifecycle

``` mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Filter as JwtAuthenticationFilter
    participant Controller as AuthController
    participant Service as AuthService
    participant Redis

    Client->>Filter: POST /api/auth/logout with access token
    Filter->>Redis: Validate current active access token
    Redis-->>Filter: Token is current
    Filter->>Controller: Continue authenticated request
    Controller->>Service: logout()
    Service->>Redis: Read active access and refresh tokens
    Service->>Redis: Store SHA-256 blacklist keys with remaining TTL
    Service->>Redis: Delete active token keys
    Service-->>Controller: Logout complete
    Controller-->>Client: 204 No Content
```

## Redis key design

The repository uses these key prefixes:

  ------------------------------------------------------------------------------
  Redis key pattern              Stored value            Purpose
  ------------------------------ ----------------------- -----------------------
  `user:access:{username}`       Current access JWT      Only the active access
                                                         token is accepted

  `user:refresh:{username}`      Current refresh JWT     Only the active refresh
                                                         token can be rotated

  `blacklist:access:{sha256}`    `revoked`               Marks a revoked access
                                                         token

  `blacklist:refresh:{sha256}`   `revoked`               Marks a revoked refresh
                                                         token
  ------------------------------------------------------------------------------

The active-token keys expire using the remaining lifetime of their
respective JWTs. Blacklist entries use the token hash as the key suffix
and expire when the corresponding JWT would expire.

## View PostgreSQL data

The Compose file creates:

-   Database: `redis_spring`
-   Username: `postgres`
-   Port: `5432`
-   Container: `redis-token-postgres`
-   Persistent volume: `postgres_data`

Connect to the database from the host using a database client such as
pgAdmin or DBeaver:

``` text
Host:     localhost
Port:     5432
Database: redis_spring
Username: postgres
Password: value of DB_PASSWORD in .env
```

Or open the PostgreSQL shell inside the container:

``` bash
docker exec -it redis-token-postgres psql -U postgres -d redis_spring
```

Useful SQL:

``` sql
-- List tables
\dt

-- View registered users
SELECT id, username, role FROM users;

-- Count registered users
SELECT COUNT(*) FROM users;
```

The `users` table is created by JPA from the `User` entity when the
application starts with the configured schema-generation setting.

## Useful Docker commands

  ---------------------------------------------------------------------------
  Command                                 Description
  --------------------------------------- -----------------------------------
  `docker compose up --build -d`          Build and start all services

  `docker compose up -d postgres redis`   Start only PostgreSQL and Redis

  `docker compose ps`                     Show Compose service status

  `docker compose logs -f app`            Follow application logs

  `docker compose logs -f postgres`       Follow PostgreSQL logs

  `docker compose logs -f redis`          Follow Redis logs

  `docker compose restart app`            Restart Spring Boot container

  `docker compose down`                   Stop and remove containers;
                                          preserve volumes

  `docker compose down -v`                Stop containers and delete named
                                          volumes
  ---------------------------------------------------------------------------

## Build and test

Build and run the Maven verification lifecycle:

**Windows**

``` powershell
.\mvnw.cmd clean verify
```

**macOS / Linux**

``` bash
./mvnw clean verify
```

The Dockerfile builds the JAR with Maven and copies it into a Java 17
runtime image. The Docker build currently uses `-DskipTests`, so run the
Maven verification command separately when validating tests.

## Security and design notes

-   **One active session per username:** the Redis keys are
    username-based. A new login replaces the previous token pair for
    that username. This is not a multi-device session design.
-   **Refresh-token rotation:** the Redis Lua script checks that the
    submitted refresh token is still current before replacing the token
    pair. This prevents two concurrent uses of the same refresh token
    from both succeeding.
-   **Bearer-token handling:** access and refresh tokens are
    credentials. Do not publish them, commit them, or write them to
    application logs.
-   **JWT secret:** `JWT_SECRET` must be Base64-encoded and decode to at
    least 32 bytes. Keep it in environment variables or a secret
    manager, not in source control.
-   **Database credentials:** the Compose defaults are intended for
    local development. Set strong credentials for any shared or
    production environment.
-   **Redis exposure:** the Compose file maps Redis to the host for
    local development. Do not expose Redis publicly; use private
    networking and appropriate authentication/TLS controls for
    production.
-   **Schema management:** `DDL_AUTO=update` is configured for
    development convenience. Use a migration tool such as Flyway or
    Liquibase and validate the schema in production.
-   **CSRF:** the security configuration disables CSRF and uses
    stateless bearer-token authentication in the Authorization header.
    Reassess this if authentication is moved into cookies.
-   **Additional controls not included:** rate limiting, account
    lockout, email verification, password reset, audit-event retention,
    multi-device sessions, and production secret management are outside
    the current implementation.

## Project structure

``` text
redis-token-management/
├── src/
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── AdminController.java
│   │   │   │   └── HomeController.java
│   │   │   ├── dto/
│   │   │   ├── exception/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   ├── service/
│   │   │   └── RedisTokenManagementApplication.java
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
├── .env.example
├── Dockerfile
├── docker-compose.yaml
├── pom.xml
└── README.md
```

## License

No license file is included in the supplied project. Add a license
before distributing the project if you intend to grant others reuse
rights.
