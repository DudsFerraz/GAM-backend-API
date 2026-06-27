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
- PostgreSQL

Create a local configuration file from the example:

```powershell
Copy-Item src/main/resources/application-local.properties.example src/main/resources/application-local.properties
```

Fill in the datasource, JWT secret, SQL logging, and CORS values in `application-local.properties`.

Run the test suite:

```powershell
.\mvnw.cmd test
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

By default, the application uses the `local` Spring profile and validates the database schema through Flyway-managed migrations.

## Documentation

Project documentation is organized under `docs/`.

- `docs/about-gam/` describes the social and religious context behind the project.
- `docs/refactor/` records architecture review notes and planned improvements.

## Status

This is an in-progress volunteer project. The codebase already contains core backend foundations, but the product and architecture are still evolving as GAM Piracicaba's real operational needs become clearer.

## Motivation

Software in this project is a means of service. Its value comes from helping a volunteer community care for people, remember commitments, organize events, and sustain a Salesian missionary presence with more clarity and continuity.
