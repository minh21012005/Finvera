# ADR-0001: Use Spring Boot 4.1.x for the Core Backend

**Status**: Accepted  
**Date**: 2026-08-17  
**Decision owners**: Finvera maintainers  
**Related specs**: [001-market-overview](../../specs/001-market-overview/spec.md)

## Context

The original SRS identified Spring Boot 3, while the generated backend scaffold
already pins Spring Boot 4.1.0 and Java 21. Leaving both statements in place
would cause feature plans to make inconsistent dependency, container, and test
choices.

Finvera is a greenfield modular monolith. It currently has no production code,
legacy library, servlet-container requirement, or external Spring portfolio
dependency that requires Spring Boot 3.x compatibility. Its initial backend
needs are Spring MVC, Security, JPA, PostgreSQL, and optional future Kafka.

Spring Boot 4.1.0 is a generally available release. It requires Java 17 or
newer, uses Spring Framework 7.0.8 or newer, and supports Servlet 6.1
containers. Java 21 is within the supported range. Spring Boot's support policy
recommends using the latest supported release and provides at least 12 months of
support for a minor version.

Sources:

- [Spring Boot 4.1.0 release announcement](https://spring.io/blog/2026/06/10/spring-boot-4/)
- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)
- [Spring Boot support policy](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)
- [Spring Boot 3.5 to 4.0 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

## Decision Drivers

- Avoid building new product code on a line that would require a planned major
  migration to reach the current platform generation.
- Preserve the existing Java 21 baseline and Boot 4.1 starter/test layout.
- Keep dependency management, security patches, observability, and testing on
  the current stable Spring platform.
- Maintain a simple modular monolith without adding compatibility layers for two
  Spring Boot major versions.

## Considered Options

### Option A: Spring Boot 3.5.x

Spring Boot 3.5 is stable and offers a more mature ecosystem, including
Undertow support. It requires Spring Framework 6.2 and Servlet 6.0. Choosing it
would require replacing the Boot 4.1 scaffold dependencies and scheduling a
3.5-to-4.x migration after product development begins.

### Option B: Spring Boot 4.1.x

Spring Boot 4.1 is the current stable release and is compatible with Java 21.
It moves to Spring Framework 7 and Servlet 6.1, uses Tomcat 11 by default, and
has a more focused module/starter layout. Direct third-party dependencies must
be checked for compatibility. Undertow is not a supported embedded option.

## Decision

Finvera's core backend MUST use **Java 21 and Spring Boot 4.1.x**. The current
pin is Spring Boot **4.1.0** in `finvera-be/pom.xml`.

Only generally available 4.1.x patch releases may be adopted, after dependency
compatibility checks and the backend test suite pass. Milestone, release
candidate, snapshot, and unverified major/minor versions are prohibited for
normal development and production use.

New direct Java dependencies, servlet containers, and Spring portfolio projects
MUST be verified as compatible with Spring Boot 4.1 / Spring Framework 7 before
they are added. The backend uses supported Servlet 6.1 containers; Undertow is
not an option unless a future ADR changes the platform decision.

## Consequences

### Positive

- The SRS, Java 21 toolchain, generated backend scaffold, and future feature
  plans have one consistent baseline.
- The project starts on the current stable Spring generation and avoids a
  foreseeable 3.5-to-4.x major migration after business code accumulates.
- Spring MVC, Security, JPA, PostgreSQL, Kafka, and observability decisions use
  one managed dependency platform.

### Negative / Trade-offs

- Libraries that only support Spring Framework 6.x cannot be introduced without
  an approved alternative or a new ADR.
- Team members must follow Boot 4's modular starter/test conventions.
- Deployment must target Servlet 6.1-compatible containers; Undertow is
  unavailable.

### Risks and Mitigations

- **Third-party incompatibility**: research and validate each direct dependency
  in the owning feature plan before adoption.
- **Accidental version drift**: keep the parent version centralized in
  `pom.xml`, use managed dependencies where possible, and run dependency and
  test checks during review.
- **Incorrectly attributed failures**: distinguish framework compatibility from
  missing environment/test configuration. The current context-load failure is
  caused by the absence of a configured test datasource, not Boot 4.1.

## Migration and Rollback

No code migration is required: the backend scaffold already uses Boot 4.1.0.
This decision aligns the documentation with the existing executable baseline.

If a required, irreplaceable product dependency cannot support Boot 4.1, stop
the affected feature plan and create a superseding ADR. A rollback to Boot 3.5
would require updating starter/test dependencies, validating all configuration
and imports, and running the complete suite before business code depends on the
newer baseline. It does not require a database-data migration by itself.

## Validation

- Confirm Java 21 compilation and context loading after the test datasource
  baseline is configured.
- For every direct dependency added, record Boot 4.1 / Framework 7 compatibility
  in the feature's `research.md` and verify it through relevant tests.
- Keep the backend on the latest verified generally available 4.1.x patch.
- Review this ADR before introducing Spring Cloud, Spring Modulith, Undertow, a
  non-standard servlet container, or a Spring portfolio project with its own
  release train.

