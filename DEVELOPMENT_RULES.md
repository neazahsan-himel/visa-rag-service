# Development Rules — Visa RAG Service

These rules govern every code generation, refactoring, and architectural decision in this project.
They apply to both human developers and AI assistants working in this codebase.

---

## 1. Code Generation Discipline

**Generate one class at a time.**
Each response produces exactly one class, interface, record, or enum. No exceptions.
If a feature requires multiple classes, list them first and wait for confirmation before proceeding to each one.

**Explain the design before writing code.**
Before generating any class, state:
- What the class does and why it is needed
- Which layer it belongs to (`presentation`, `application`, `domain`, `infrastructure`) and which sub-package within that layer
- Which interfaces it implements or which classes it depends on
- Any design decision worth calling out

Wait for approval if the design deviates from prior decisions.

**No placeholder code.**
Every method must have a real implementation or throw `UnsupportedOperationException` with an explicit TODO comment explaining what is missing and why. Never write `// TODO` on an empty method body and leave it there silently.

**No speculative code.**
Only implement what is explicitly requested. Do not add methods, fields, or classes in anticipation of future needs. YAGNI applies strictly here.

---

## 2. Architectural Boundaries

**Respect the Clean Architecture dependency rule — always.**

```
presentation → application → domain ← infrastructure
```

- `domain` must never import from `application`, `infrastructure`, `presentation`, or any Spring class.
- `application` must never import from `infrastructure` or `presentation`.
- `infrastructure` implements `domain` interfaces; it may import Spring and third-party libraries.
- `presentation` depends only on `application` commands and results — never on domain internals directly.

Violating this rule is not a shortcut. It is a defect.

**Never create a package that is not defined in `CLAUDE.md`.**
If a new package seems necessary, raise it as a question before creating it.

**Keep the architecture consistent with `CLAUDE.md`.**
Before generating code, verify that the class placement, naming, and dependencies align with the package structure and principles documented in `CLAUDE.md`. If they conflict, stop and ask.

---

## 3. Dependency Management

**Do not introduce a new Maven dependency unless explicitly requested.**
Before adding any dependency to `pom.xml`, state:
- The dependency coordinates (`groupId:artifactId:version`)
- Why it is needed
- Whether an existing dependency or standard library already covers the need

Wait for approval before modifying `pom.xml`.

**Never add a dependency that duplicates an existing one.**
Check `pom.xml` before suggesting a library. If Spring Boot or Spring AI already provides the capability, use it.

**Prefer managed versions.**
Use `spring-boot-starter-parent` managed versions wherever possible. Only specify an explicit version when the managed version is insufficient.

---

## 4. File Modification Scope

**Do not modify files unrelated to the current task.**
If a task requires changing class A, only class A changes. If class B also needs updating as a consequence, state that explicitly and wait for a separate instruction.

**Do not reformat or restyle unrelated code.**
Whitespace, import ordering, and naming fixes in untouched lines are out of scope unless the task is explicitly a cleanup task.

**Do not modify `CLAUDE.md`, `ARCHITECTURE.md`, or `DEVELOPMENT_RULES.md` during feature work.**
These documents are updated only when there is a deliberate, discussed change to project standards.

---

## 5. Naming and Code Style

**Follow Java naming conventions strictly.**

| Construct | Convention | Example |
|---|---|---|
| Class / Interface | `UpperCamelCase` | `VisaQueryService` |
| Method / Variable | `lowerCamelCase` | `retrieveContext()` |
| Constant | `UPPER_SNAKE_CASE` | `DEFAULT_TOP_K` |
| Package | `lowercase` | `com.neaz.travel.visa.domain` |
| Database column | `snake_case` | `passport_iso` |
| Qdrant payload field | `snake_case` | `visa_type_code` |

**Class names must reflect responsibility.**
A class named `Manager`, `Helper`, `Util`, or `Handler` (without a qualifying noun) is not acceptable. Name it after what it actually does: `HybridRetriever`, `ConfidenceScorer`, `CitationBuilder`.

**Constructor injection only.**
`@Autowired` on a field is never acceptable. All dependencies are declared `final` and injected via constructor.

```java
// Correct
public class VisaQueryService {
    private final EmbeddingPort embeddingPort;
    private final VectorSearchPort vectorSearchPort;

    public VisaQueryService(EmbeddingPort embeddingPort, VectorSearchPort vectorSearchPort) {
        this.embeddingPort = embeddingPort;
        this.vectorSearchPort = vectorSearchPort;
    }
}

// Never
@Service
public class VisaQueryService {
    @Autowired
    private EmbeddingPort embeddingPort; // VIOLATION
}
```

---

## 6. Domain Purity

**Domain classes must not import Spring, JPA, or any infrastructure library.**
The `domain` package is pure Java. Annotations such as `@Entity`, `@Column`, `@Service`, or `@Component` must never appear in `domain` classes.

**Domain objects are immutable where possible.**
Use `record` for value objects. Use `final` fields with no setters for entities. Mutation is allowed only when the domain model genuinely requires state change.

**No business logic in controllers or JPA entities.**
Controllers validate input and delegate. JPA entities are persistence structures — they do not carry domain behaviour.

---

## 7. RAG-Specific Rules

**Never fabricate information.**
The prompt template must always instruct the LLM to respond only from provided context. Do not remove or soften this constraint.

**Confidence scoring is mandatory.**
Every query response must include a computed confidence score. A response below the configured threshold must return the insufficient-context message, not a low-confidence answer.

**Source citation is mandatory.**
Every factual statement in an answer must be traceable to a specific `document_chunk` row and a Qdrant point. The `CitationBuilder` must be called on every successful answer path.

**Metadata filtering must always be applied.**
A retrieval call without a `passport_iso` + `destination_iso` filter is a defect. Never perform an unfiltered vector search in production query paths.

---

## 8. Testing

**Every public class in `domain` and `application` must be unit-testable without Spring context.**
Tests in these layers use plain JUnit 5 and Mockito. No `@SpringBootTest` in unit tests.

**Infrastructure adapters are tested with integration tests.**
Integration tests are in a separate source set or clearly marked with a `@Tag("integration")` annotation and require running Docker services.

**Test method names follow the pattern: `methodName_condition_expectedBehavior`.**

```java
// Example
@Test
void retrieveContext_whenNoBdPassportChunksFound_throwsInsufficientContextException() { }
```

---

## 9. Git Commits

Follow the commit style defined in `CLAUDE.md`:

```
feat: add hybrid retriever with RRF scoring
fix: apply metadata filter before vector search
refactor: extract confidence scorer to domain service
docs: update architecture data flow diagram
test: add unit tests for citation builder
```

One logical change per commit. Do not bundle unrelated fixes into a single commit.

---

## 10. When in Doubt

- **Ask before acting.** An incorrect architectural decision is harder to fix than a delayed one.
- **Reference `CLAUDE.md` and `ARCHITECTURE.md` first.** Most questions are already answered there.
- **State assumptions explicitly.** If a rule is ambiguous, name the assumption being made before proceeding.