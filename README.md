# GAM Piracicaba API

> Portuguese version: [README.pt-BR.md](README.pt-BR.md)

Backend API for the GAM Piracicaba management platform, a volunteer-built software project created to support the mission, organization, and pastoral work of GAM Piracicaba.

GAM Piracicaba is a Salesian youth missionary group from Piracicaba, Brazil. The group runs social, educational, and evangelizing activities such as the weekly oratory, missionary actions, and support for Salesian school missionary weeks.

## Purpose

This project is being developed voluntarily to help GAM Piracicaba organize its internal operations with more care and reliability.

The API is intended to support:

- member and account management;
- role-based access control;
- event registration and search;
- oratory and Mass event records;
- presence tracking;
- location records;
- authentication with access and refresh tokens;
- auditable and soft-deletable persistence.

The goal is not only to create software, but to reduce operational friction for volunteers so they can spend more energy on mission, formation, service, and community.

## Project Context

To understand the group that motivates this system, see:

- [About GAM Piracicaba](docs/about-gam/gam-piracicaba.md)

## Technology Stack

- Java 21
- Spring Boot 3.5.7
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- MapStruct
- Lombok
- JWT authentication
- Maven

## Current Architecture

The project is a Spring Boot REST API with feature-oriented domain areas such as accounts, members, events, locations, presences, and RBAC.

It currently uses explicit layers and patterns, including controllers, use-case services, repositories, DTOs, mappers, persistence entities, domain objects, custom exceptions, dynamic search specifications, auditing, and soft delete behavior.

Architecture notes and refactor direction are documented in:

- [Project Architecture Review](docs/refactor/project-refactor-roadmap.md)
- [Architecture Refactor Roadmap](docs/refactor/architecture-refactor-roadmap.md)

## Getting Started

Prerequisites:

- Java 21
- Maven, or the included Maven Wrapper
- Docker Desktop

Run the test suite:

```powershell
.\mvnw.cmd test
```

Run the application with the development shortcut:

```powershell
$env:JWT_SECRET_KEY = "<base64-encoded-32-byte-secret>"
.\mvnw.cmd -Pdev
```

This command activates the Maven profile named `dev`. That Maven profile is only a shortcut: it runs the `spring-boot:run` goal and passes the Spring profile named `dev` to the application. The Spring profile then loads `application-dev.properties`.

The development profile requires `JWT_SECRET_KEY` to be set in the environment. Use a local-only base64-encoded secret with at least 32 bytes of decoded key material.

The development Spring profile uses Spring Boot Docker Compose support to start PostgreSQL from `compose.yml` when needed. The PostgreSQL container is kept running after the application exits so later application restarts are faster.

The development and integration-test databases use PostgreSQL 18 because the migrations call PostgreSQL's built-in `uuidv7()` function. Current Flyway versions may print a warning that PostgreSQL 18 support is newer than its tested compatibility range; that warning is expected until the managed Flyway version is upgraded.

Stop the development database when you are done:

```powershell
docker compose stop
```

Reset the development database and remove its volume:

```powershell
docker compose down -v
```

The application does not activate a Spring profile by default. Use `.\mvnw.cmd -Pdev` for local development. Plain `.\mvnw.cmd spring-boot:run` intentionally does not load development settings or start Docker Compose. Flyway validates and migrates the PostgreSQL schema during startup.

## Documentation

Project documentation is organized under `docs/`.

- `docs/about-gam/` describes the social and religious context behind the project.
- `docs/refactor/` records architecture review notes and planned improvements.

## Status

This is an in-progress volunteer project. The codebase already contains core backend foundations, but the product and architecture are still evolving as GAM Piracicaba's real operational needs become clearer.

## Motivation

Software in this project is a means of service. Its value comes from helping a volunteer community care for people, remember commitments, organize events, and sustain a Salesian missionary presence with more clarity and continuity.
