# Architecture Decision Record 0001: Begin with a Modular Monolith

## Status

Accepted

## Date

15-08-2026

## Context

Striply is a portfolio project developed by one person, main goal of the project its learning. Striply's
domain requirements might change during development. Expected workload for demo does not require independent service
scaling. Starting with distributed services would add unnecessary deployment complexity, While unstructured monolith
would keep be simpler to implement refactoring the codebase for microservices structure would be much harder.

## Decision

Striply will begin as one Spring Boot application organized as a modular monolith.

The backend is divided into feature based modules with enforced dependency boundaries. Features may interact only
through explicit contracts or events, not another feature's repositories. The current module convention is documented
in [Backend Modules](../backend-modules.md).

### Data and Deployment

Striply initially uses one PostgreSQL database. Business operations may use local database transactions when an
required. Cross-feature transactions should be avoided.

## Alternatives Considered

### Unstructured Monolith

One application without enforced feature boundaries would be simpler initially. It was rejected because controllers,
services, and repositories could easily become coupled across unrelated features. Making it harder to refactor into
microservices at later stage of the project.

### Microservices

Each feature could be deployed as an independent service. This approach is deferred until
later stages of the project until business domain and feature boundaries stabilize.


### Separate Database Schema per Feature

Each feature could own a PostgreSQL schema. This was deferred because added complexity 
would slow down development and require costly refactors after the feature boundaries have stabilized.

## Consequences

### Benefits

- Development, testing, debugging, and deployment remain simple.
- Explicit feature boundaries allow for easier refactor to microservices later.

### Costs

- A process failure can affect the entire backend.
- Services share resources.
- The shared database makes cross-feature access possible.
- Features cannot scale independently until a worker or service is separated.
All costs are deemed acceptable for the development stage

## Risks

- **Boundary erosion:** package rules, architecture tests, and code review must prevent access to another feature's
  internals.
- **Database coupling:** each feature must own its repositories, and cross-feature constraints or transactions must be
  documented.

## Related Documentation

- [Backend modules](../backend-modules.md)
- [Non-functional requirements](../non-functional-requirements.md)
- [Container architecture](../container-diagram.md)
