# GAM Piracicaba API

> Portuguese version: [README.pt-BR.md](README.pt-BR.md)

Backend API for the GAM Piracicaba management platform, a volunteer-built software project created to support the mission, organization, and pastoral work of GAM Piracicaba.

GAM Piracicaba is a Salesian youth missionary group from Piracicaba, Brazil. The group runs social, educational, and evangelizing activities such as the weekly Oratorio, missionary actions, and support for Salesian school missionary weeks.

## Purpose

This project is being developed voluntarily to help GAM Piracicaba organize its internal operations with more care and reliability.

The API is intended to support:

- member and account management;
- role-based access control;
- event registration and search;
- Oratorio and Missa event records;
- Oratoriano records;
- presence tracking;
- location records;
- authentication with access and refresh tokens;
- auditable and soft-deletable persistence.

The goal is not only to create software, but to reduce operational friction for volunteers so they can spend more energy on mission, formation, service, and community.

## Project Context

To understand the group that motivates this system, see:

- [About GAM Piracicaba](docs/about-gam/gam-piracicaba.md)

Canonical project terminology is maintained in the [GAM ubiquitous language](docs/ubiquitous-language.md).

## Technology Stack

- Java 21
- Spring Boot 3.5.16
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- MapStruct
- Lombok
- JWT authentication
- Maven
- Testcontainers
- REST Assured
- JUnit Platform test suites

## Current Architecture

The project is a Spring Boot REST API organized around feature-oriented domain areas such as accounts, members, events, Oratorios, Missas, locations, presences, Oratorianos, RBAC, security, and shared infrastructure.

The code currently uses explicit layers and patterns, including web controllers, application use cases, DTOs/RDTOs, mappers, entity and domain loaders, domain objects, persistence entities, repositories, custom exceptions, dynamic search specifications, security specifications, auditing, activity logging, refresh tokens, and soft delete behavior.

Architecture and implementation conventions are documented in:

- [Software Guidelines](docs/software-guidelines/)
- [Documentation Guidelines](docs/documentation-guidelines/README.md)

## Running Locally

For local setup, backend startup, Maven commands, Docker Compose commands, and dependency checks, see [Running the System](docs/dev-guidelines/running-the-system/README.md).

## Documentation

Project documentation is organized under `docs/`.

- `docs/about-gam/` describes the social and religious context behind the project.
- `docs/dev-guidelines/` contains practical human developer guides for running the backend, using Docker and Maven, inspecting dependencies, and working with agents in this project.
- `docs/documentation-guidelines/` defines the project documentation system, including Requirement Specifications, ADRs, diagrams, OpenAPI notes, videos, agent workflow, and source-of-truth rules.
- `docs/software-guidelines/` records backend implementation conventions for package organization, controllers, application services, domain models, persistence, migrations, mappers, exceptions, interfaces, search specifications, RBAC, audit logs, and tests.
- `docs/ubiquitous-language.md` is the global glossary for canonical GAM domain terms.
- `docs/ideas/` contains exploratory notes that are not yet accepted requirements.

## Status

This is an in-progress volunteer project. The codebase already contains core backend foundations, but the product and architecture are still evolving as GAM Piracicaba's real operational needs become clearer.

## Motivation

Software in this project is a means of service. Its value comes from helping a volunteer community care for people, remember commitments, organize events, and sustain a Salesian missionary presence with more clarity and continuity.
