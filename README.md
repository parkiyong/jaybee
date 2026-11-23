# Jaybee

**Jaybee** is a clean-room, MIT-licensed Java 17 workflow engine with a distinct, idiomatic `io.github.parkiyong.jaybee.*` native API, a strict jPDL 4.4 XML parser, and an optional compatibility adapter (`jaybee-jbpm4-adapter`) that bridges to the unmodified external `org.jbpm.jbpm4:jbpm-api:4.4` artifact.

## License & Clean-Room Design

- **License**: MIT (see [`LICENSE`](LICENSE))
- **Zero runtime third-party dependencies** in `jaybee-api`, `jaybee-core`, and `jaybee-jpdl`
- **Idiomatic native API**: `jaybee-api` uses Java 17 records (`StartProcessRequest`, `SignalRequest`, `PageRequest`), typed enums (`ExecutionStatus`, `DeploymentStatus`, `SortOrder`), `Optional`, and `java.time.Instant` / `java.nio.file.Path`.
- **`jaybee-jbpm4-adapter`** is the sole compatibility boundary declaring `compileOnly("org.jbpm.jbpm4:jbpm-api:4.4")` (LGPL v2.1) with a strict repository content filter allowing only `org.jbpm.jbpm4:jbpm-api`, so consumers supply the unmodified `jbpm-api-4.4.jar` at runtime without Jaybee modifying or redistributing any LGPL binaries (see [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)).

## Modules

| Module | Description |
| --- | --- |
| `jaybee-api` | Idiomatic Java 17 public API (`JaybeeEngine`, `RepositoryService`, `RuntimeService`, `StartProcessRequest`, `SignalRequest`), strongly-typed query & model types (`ExecutionStatus`, `DeploymentStatus`, `PageRequest`, `SortOrder`), immutable process graph model, and SPIs (`ProcessStore`, `ExpressionEvaluator`, `ProcessDefinitionParser`, `HandlerResolver`, `DecisionHandlerBridge`). |
| `jaybee-core` | Standalone workflow execution engine, thread-safe `InMemoryProcessStore` with per-instance locking and optimistic versioning, reflective handler resolver, and zero-dependency clean-room EL evaluator (`#{...}`). |
| `jaybee-jpdl` | Strict fail-fast jPDL 4.4 XML parser (`start`, `state`, `decision`, `fork`, `join`, `end`, `end-cancel`, `end-error`, `g` coordinates, `<handler>` field/property descriptors). |
| `jaybee-jbpm4-adapter` | Optional facade adapting `JaybeeEngine` to `org.jbpm.api.ProcessEngine`, `RepositoryService`, `ExecutionService`, and `org.jbpm.api.jpdl.DecisionHandler`. |

## Quick Start

### Native Jaybee API

```java
JaybeeEngine engine = JaybeeEngineBuilder.create()
        .registerParser(new JpdlProcessParser())
        .build();

engine.repository().newDeployment()
        .addStringResource("hello.jpdl.xml", jpdlXml)
        .deploy();

ProcessInstance pi = engine.runtime()
        .startProcess(StartProcessRequest.byKey("hello"));
```

### jBPM 4.4 Adapter (`org.jbpm.api.*`)

```java
org.jbpm.api.ProcessEngine engine = JaybeeJbpm4Adapter.createDefaultProcessEngine();
engine.getRepositoryService().createDeployment()
        .addResourceFromString("hello.jpdl.xml", jpdlXml)
        .deploy();

org.jbpm.api.ProcessInstance pi = engine.getExecutionService()
        .startProcessInstanceByKey("hello");
```

## Build & Test

```bash
./gradlew test
```