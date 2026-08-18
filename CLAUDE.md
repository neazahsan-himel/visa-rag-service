# Visa RAG Service

## Project Overview

This project is a production-grade Retrieval-Augmented Generation (RAG) service for providing accurate visa information using official government and embassy documents.

This is **NOT** a demo chatbot or a simple "Chat with PDF" application.

The project must follow enterprise software engineering principles and be suitable as a senior Java backend portfolio project.

---

# Primary Goal

Build a scalable, maintainable, production-ready AI service that retrieves visa information from official documents and generates grounded responses with citations.

The first supported destination is:

* Passport: Bangladesh
* Destination: India

Future versions will support multiple countries.

---

# Technology Stack

Backend

* Java 21
* Spring Boot 3.5.x
* Spring AI
* Maven

Database

* PostgreSQL

Vector Database

* Qdrant

Caching

* Redis (Future)

LLM

* OpenAI (Configurable)
* Ollama (Configurable)

Document Processing

* Apache Tika
* PDFBox
* JSoup

Infrastructure

* Docker
* Docker Compose

Testing

* JUnit 5
* Mockito

---

# Architecture

Use Clean Architecture.

presentation

↓

application

↓

domain

↓

infrastructure

Business logic must never depend on Spring Framework classes.

---

# Package Structure

```
com.neaz.travel.visa
├── config/
├── common/
│   ├── exception/
│   ├── model/
│   └── util/
├── presentation/
│   ├── controller/
│   ├── dto/
│   └── mapper/
├── application/
│   ├── query/
│   │   ├── retrieval/
│   │   ├── prompt/
│   │   └── citation/
│   ├── ingestion/
│   ├── command/
│   ├── result/
│   └── port/
├── domain/
│   ├── model/
│   ├── repository/
│   └── service/
└── infrastructure/
    ├── persistence/
    ├── vectorstore/
    ├── embedding/
    ├── llm/
    └── document/
```

Never create a package outside this structure.
If a new package seems necessary, raise it as a question before creating it.

---

# Coding Principles

Always follow:

* SOLID
* Clean Code
* Single Responsibility Principle
* Constructor Injection
* Immutability where appropriate
* Interface-driven development

Never use field injection.

Never use static utility classes unless necessary.

Avoid God classes.

---

# RAG Principles

Always use:

Hybrid Search

Metadata Filtering

Vector Search

Prompt Templates

Source Citation

Confidence Score

No Hallucination

If context is insufficient, respond with:

"I couldn't find sufficient official information."

Never fabricate visa rules.

---

# Data Sources

Only official sources.

Examples

* Embassy documents
* Government websites
* Official visa PDFs
* Immigration guidelines

Never rely on blogs or unofficial travel websites.

---

# Knowledge Base

knowledge-base/

India/

Tourist/

Business/

Medical/

Student/

Every document must include metadata.

Country

Visa Type

Passport

Source

Last Updated

Document Version

---

# Database Responsibility

PostgreSQL

Stores

* Visa Rules
* Countries
* Metadata
* Document Information
* Audit Data

Qdrant

Stores

* Embeddings
* Chunks

Never store business data only in Qdrant.

---

# Project Modules

Current

* Visa Query
* Document Ingestion
* Embedding Generation
* Retrieval
* Citation

Future

* Destination RAG
* Airline Policy RAG
* Hotel Policy RAG
* Travel Planner Agent

Design everything so future modules can reuse existing components.

---

# Code Generation Rules

Whenever generating code:

1. Explain the design before writing code.

2. Generate production-quality code.

3. Prefer readability over cleverness.

4. Follow Java naming conventions.

5. Include JavaDoc for public classes.

6. Write unit-testable code.

7. Avoid unnecessary frameworks.

8. Never generate placeholder code without explanation.

9. Generate one feature at a time.

10. Ask before making architectural changes.

---

# Git Commit Style

Examples

feat: add visa query endpoint

feat: implement PDF ingestion pipeline

feat: integrate Qdrant vector search

feat: implement hybrid retrieval

fix: improve metadata filtering

refactor: clean application layer

docs: update architecture

---

# Documentation

Every new feature should update:

README.md

docs/architecture.md

docs/api.md

---

# Project Philosophy

This project should look like software developed by a Senior Backend Engineer.

Focus on:

Maintainability

Scalability

Accuracy

Testability

Performance

Security

Avoid shortcuts that reduce code quality.

Always think in terms of production software rather than tutorials.