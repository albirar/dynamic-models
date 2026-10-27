# New Model Filtering Implementation - Analysis and Proposal

[Català](nova-implementacio.md) · **English** · [Español](nova-implementacio.es.md)

## Table of Contents
1. [Introduction](#1-introduction)
2. [Comparison of Implementations](#2-comparison-of-implementations)
3. [Version Differences Analysis](#3-version-differences-analysis)
4. [Detailed Implementation Proposal](#4-detailed-implementation-proposal)
5. [Migration Plan](#5-migration-plan)
6. [Conclusion and Recommendation](#6-conclusion-and-recommendation)

---

## 1. Introduction

### 1.1. Context

The project has a model filtering implementation based on:
- `@FilteredView` on fields
- `FilterInterceptor` (HandlerInterceptor)
- `FilteredViewPropertyFilter` (Jackson 2 SimpleBeanPropertyFilter)
- `FilterContext` (ThreadLocal)
- `@JsonFilter` on models
- A Jackson 2 `ObjectMapper` with a `FilterProvider` registered in `FilterConfiguration`

The document [arquitectura-models-dinamics.en.md](./arquitectura-models-dinamics.en.md) proposes an alternative architecture based on:
- `@ModelBasic` and `@ModelSections` on fields
- `ModelFilteringAdvice` (ResponseBodyAdvice)
- `ModelFilteringModule` (Jackson module)
- `ModelSelection` (immutable, stateless)

### 1.2. Goal

This document analyzes the differences between both implementations, assesses compatibility with Spring Boot 4.1.x (Spring Framework 7, Jackson 3) and describes the implementation of the new architecture adapted to these versions.

### 1.3. Status of the current implementation

**The current implementation does not work over HTTP with Spring Boot 4.1.1.** Verified by starting the application:

```
GET /vehicles      → 500  Cannot resolve PropertyFilter with id 'vehicleModelFilter'; no FilterProvider configured
GET /brands        → 500  (same error with 'brandFilter')
GET /vehicles?m=Z  → 400  (the interceptor validation does work)
```

Cause: Spring Boot 4 serializes MVC responses with **Jackson 3** (`tools.jackson`, `JacksonJsonHttpMessageConverter` converter). The `ObjectMapper` in `FilterConfiguration` is **Jackson 2** (`com.fasterxml.jackson.databind`) and MVC never uses it. Jackson 3 keeps the `com.fasterxml.jackson.annotation` annotations package, so it does read `@JsonFilter`, but its mapper has no `FilterProvider` and serialization fails.

The tests did not detect it because none of them goes through the HTTP serialization layer: the controller tests invoke the Java method directly and the filter tests build their own Jackson 2 `ObjectMapper`.

---

## 2. Comparison of Implementations

### 2.1. General Comparison

| **Aspect** | **Current Implementation** | **Proposed Architecture** | **Assessment** |
|-------------|--------------------------|---------------------------|---------------|
| **Main Mechanism** | Interceptor (preHandle) | ResponseBodyAdvice (before writing) | ✅ Proposed is more natural |
| **State** | ThreadLocal (FilterContext) | Immutable per-response `FilterProvider` (write hint) | ✅ Proposed is safer |
| **Validation** | HTTP 400 for invalid codes | HTTP 400 for invalid codes and repeated values | ✅ Equivalent, proposed is more complete |
| **Code Format** | Comma-separated (`B,P,E`), case-insensitive | Concatenated (`BPE`), case-sensitive | ⚠️ API change |
| **Annotations** | `@FilteredView(filters={...})` | `@ModelBasic` + `@ModelSections({...})` | ✅ Proposed is more expressive |
| **Cascade** | Via ThreadLocal | Via the `ObjectWriter`'s single `FilterProvider` | ✅ Proposed is more elegant |
| **Vary Header** | Not implemented | Always on filtered endpoints | ✅ Proposed is better for caches |
| **Isolation** | Global Jackson 2 `ObjectMapper` (not used by MVC) | Private copy of the `JsonMapper` for the server MVC converter | ✅ Proposed is more isolated |
| **Boot 4 Compatibility** | ❌ Broken (HTTP 500) | ✅ Designed for Spring 7 / Jackson 3 | ✅ Proposed |

### 2.2. Component Comparison

#### Annotations

| **Aspect** | **Current** | **Proposed** |
|-------------|------------|---------------|
| **Purpose** | `@FilteredView` on fields that can be filtered | `@ModelBasic` (always included) + `@ModelSections` (optional) |
| **Behavior of unannotated fields** | Always included | **Excluded** if the bean has any mark |
| **Combination validation** | Does not validate | **Rejects** `@ModelBasic` + `@ModelSections` on the same property |
| **Needs `@JsonFilter` on the model** | Yes | **No**: the module assigns the filter to marked beans |

**Current Example:**
```java
@JsonFilter("vehicleModelFilter")
public class VehicleModelBean {
    @FilteredView(filters = {FiltersModel.Brand})
    private BrandBean brand;
    private String model;
}
```

**Proposed Example:**
```java
public class VehicleModelBean {
    @ModelSections(ModelPart.BRAND)
    private BrandBean brand;
    @ModelBasic
    private String model;
}
```

#### Filtering Mechanism

| **Aspect** | **Current** | **Proposed** |
|-------------|------------|---------------|
| **Technology** | `SimpleBeanPropertyFilter` + `@JsonFilter` (Jackson 2) | `AnnotationIntrospector` + `PropertyFilter` (Jackson 3) |
| **Integration** | Separate Jackson 2 `ObjectMapper` | Server JSON converter with a copy of Boot's `JsonMapper` |
| **Impact** | None on MVC (not used); breaks serialization of beans with `@JsonFilter` | Only the server MVC converters; HTTP client and shared `JsonMapper` untouched |
| **Cascade** | ThreadLocal | The `ObjectWriter`'s `FilterProvider`, shared by the whole graph |
| **Concurrency** | ThreadLocal, problematic in async processing | No mutable state; one `FilterProvider` per response |

#### Configuration

| **Aspect** | **Current** | **Proposed** |
|-------------|------------|---------------|
| **Property** | `spring.application.models.filtered` (now ignored) | `models.filtratge` |
| **Conditions** | None (currently always active) | Servlet + `JsonMapper` + Spring MVC + property |
| **Registration** | Scanned `@Configuration` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

#### HTTP Contract

| **Aspect** | **Current** | **Proposed** |
|-------------|------------|---------------|
| **Parameter** | `m` (query) | `m` (query) |
| **Header** | `x-filter-model` | `X-Model-Sections` |
| **Source** | Parameter and, if absent, header | Parameter and, if absent, header (header ignored and not validated if there is a parameter) |
| **Validation** | 400 for invalid codes | 400 for invalid codes or repeated value |
| **Format** | `B,P,E` | `BPE` |
| **Vary Header** | Not implemented | `Vary: X-Model-Sections` on every response of filtered endpoints |

### 2.3. Advantages of the Current Implementation

✅ **Simpler**: fewer components
✅ **Comma-separated code format**: perhaps more human-readable

### 2.4. Disadvantages of the Current Implementation

❌ **Does not work with Spring Boot 4**: depends on Jackson 2, which MVC no longer uses (HTTP 500).

❌ **ThreadLocal problems**:
   - Requires a manual `FilterContext.clear()` in `afterCompletion`
   - Problematic in asynchronous processing (`DeferredResult`, `@Async`, virtual threads switching threads)

❌ **Less robust**:
   - Does not validate annotation combinations
   - Does not distinguish between basic and optional fields
   - Forces declaring `@JsonFilter` on every model, and the model fails in any mapper without a `FilterProvider`

❌ **Less standard**:
   - Does not use `ResponseBodyAdvice`
   - Does not implement `Vary`

### 2.5. Advantages of the Proposed Architecture

✅ **Safer**: no `ThreadLocal`; the selection is an immutable object bound to the writing of each response.

✅ **More isolated**: only the server JSON converter uses the mapper with the module. Boot's `JsonMapper` and the client converters (`RestClient`, `RestTemplate`) are unchanged.

✅ **More standard**: `ResponseBodyAdvice` + write hints are the mechanism Spring 7 provides for per-response serialization adjustments.

✅ **More robust**: strict validation, distinction between basic and sections, detection of incorrect configurations.

✅ **More flexible**: query parameter and header usable together, with names customizable per endpoint.

---

## 3. Version Differences Analysis

### 3.1. Spring Boot 2.5.x vs 4.1.1

The original architecture was written for Spring Boot 2.5 (Spring 5, Jackson 2.12, `javax.servlet`). Spring Boot 4.1.1 changes the foundation significantly.

#### Spring Components

| **Component** | **Spring Boot 2.5.x** | **Spring Boot 4.1.1 (Spring 7.0.x)** | **Impact** |
|---------------|----------------------|----------------------|-------------|
| **Servlet API** | `javax.servlet` | `jakarta.servlet` | Package change |
| **JSON converter** | `MappingJackson2HttpMessageConverter` | `JacksonJsonHttpMessageConverter` (Jackson 3). The Jackson 2 one is **deprecated** | The Jackson 3 one must be used |
| **`MappingJacksonValue`** | Usual mechanism for views/filters | **Deprecated**. The Jackson 3 converter rejects it (`UnsupportedOperationException: MappingJacksonValue is not supported, use hints instead`) | Cannot be used |
| **`ResponseBodyAdvice`** | `beforeBodyWrite` | `beforeBodyWrite` + **`determineWriteHints`** | Serialization options are passed as hints |
| **Hints supported by the converter** | — | `JsonView` (key `com.fasterxml.jackson.annotation.JsonView`) and `FilterProvider` (key `tools.jackson.databind.ser.FilterProvider`) | The per-response filter is passed as a hint |
| **Converter registration** | `WebMvcConfigurer.configureMessageConverters(List)` | `HttpMessageConverters.ServerBuilder`; Boot applies `ServerHttpMessageConvertersCustomizer` beans in order. The `List` variant is deprecated | Customize via customizer |
| **Auto-configuration** | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` + `@AutoConfiguration` | New format |

#### Jackson Components

| **Component** | **Jackson 2.12 (Boot 2.5)** | **Jackson 3.1 (Boot 4.1)** |
|---------------|----------------------|----------------------|
| **Package** | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| **Annotations** | `com.fasterxml.jackson.annotation` | `com.fasterxml.jackson.annotation` (**unchanged**) |
| **Mapper** | Mutable `ObjectMapper` | Immutable `JsonMapper`; created with `JsonMapper.builder()` or `mapper.rebuild()` |
| **Serialization context** | `SerializerProvider` | `SerializationContext` |
| **Serializers** | `JsonSerializer`, `BeanSerializerModifier` | `ValueSerializer`, `ValueSerializerModifier` |
| **Modules** | `com.fasterxml.jackson.databind.Module` | `tools.jackson.databind.JacksonModule` (`SimpleModule` still exists) |
| **`java.time` / `Optional`** | Separate `jsr310` / `jdk8` modules | Built into `jackson-databind` |
| **Dates** | `WRITE_DATES_AS_TIMESTAMPS` enabled by default | Disabled by default (ISO-8601) |

### 3.2. Spring Boot 4 Adaptation Decisions

1. **Selection as a `FilterProvider` hint, not as an `ObjectWriter` attribute.** The Jackson 3 converter applies the hint's `FilterProvider` with `ObjectWriter.with(filters)` for that write. There is no need to subclass or decorate the converter, and the hint is per response.
2. **`AnnotationIntrospector` instead of `BeanSerializerModifier`.** The module assigns a filter id (`findFilterId`) only to beans with `@ModelBasic`/`@ModelSections`. Beans without marks receive no filter and are serialized exactly the same. Models do not need `@JsonFilter`.
3. **Behavior without selection.** The private copy of the mapper has a default `FilterProvider` that lets every property through. Without a hint, the output is identical to Jackson without the module.
4. **Private mapper for MVC.** A `ServerHttpMessageConvertersCustomizer` ordered after Boot's replaces the server JSON converter with a `JacksonJsonHttpMessageConverter` built with `jsonMapper.rebuild()` + the module. Boot's `JsonMapper` bean and the client converters are unchanged.
5. **Passing the selection between `beforeBodyWrite` and `determineWriteHints`.** Spring first calls `beforeBodyWrite` (which receives the request and the response) and then `determineWriteHints` (which does not). The advice validates the selection and adds `Vary` in `beforeBodyWrite`, and stores the `ModelSelection` as a **request** attribute. `determineWriteHints` retrieves it from the current request. It is state bound to the request, not shared; it is not stored in any singleton field, custom `ThreadLocal` or mapper.
6. **Library outside component scanning.** `ModelFilteringAdvice` must carry `@ControllerAdvice` (Spring only discovers advices annotated at type level), which is a `@Component` stereotype. To prevent it from being registered without the property enabled, the library package (`cat.albirar.dynmod.filtering`) is kept outside the application scan (`cat.albirar.dynmod.demo`) and is only loaded through auto-configuration.

### 3.3. Compatibility Conclusion

✅ **The architecture is applicable to Spring Boot 4.1** keeping the observable contract of the original document. The internal mechanism changes (`FilterProvider` hint + `AnnotationIntrospector` instead of `ObjectWriter` attribute + `BeanSerializerModifier`), not the behavior.

---

## 4. Detailed Implementation Proposal

### 4.1. Package Structure

```
src/main/java/cat/albirar/dynmod/
├── filtering/                               # Filtering "library" (outside the app scan)
│   ├── model/                               # No Spring or Jackson databind dependencies
│   │   ├── ModelPart.java                   # Sections (B, M, P, E)
│   │   ├── ModelBasic.java
│   │   ├── ModelSections.java
│   │   ├── ModelSelection.java              # Immutable parser
│   │   └── InvalidModelSelectionException.java
│   ├── jackson/                             # Jackson 3 integration, without Spring
│   │   ├── ModelFilteringModule.java
│   │   ├── ModelFilteringAnnotationIntrospector.java
│   │   ├── ModelSectionsPropertyFilter.java
│   │   └── ModelFilterProvider.java
│   └── web/                                 # Spring MVC integration
│       ├── ModelFiltering.java              # Opt-in (no Spring dependencies)
│       ├── ModelFilteringAdvice.java
│       ├── ModelFilteringException.java     # 400
│       └── ModelFilteringAutoConfiguration.java
└── demo/                                    # Application (scanBasePackages)
```

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
cat.albirar.dynmod.filtering.web.ModelFilteringAutoConfiguration
```

> Note: in a real project each service would define its own `ModelPart` (sections belong to the domain). In this demo it is part of the `filtering.model` package for simplicity.

### 4.2. Components

#### 4.2.1. Annotations and enums

```java
public enum ModelPart {
    BRAND('B', "Brand information"),
    MARKET('M', "Market data"),
    PARTS('P', "Vehicle parts"),
    ECONOMIC('E', "Economic data");

    private final char code;
    private final String description;
    // constructor, getters, static Optional<ModelPart> fromCode(char)
}

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface ModelBasic {}

@Retention(RUNTIME) @Target({FIELD, METHOD})
public @interface ModelSections { ModelPart[] value() default {}; }

@Retention(RUNTIME) @Target({TYPE, METHOD})
public @interface ModelFiltering {
    String DEFAULT_QUERY_PARAM = "m";
    String DEFAULT_HEADER = "X-Model-Sections";

    String queryParam() default DEFAULT_QUERY_PARAM;
    String header() default DEFAULT_HEADER;
}
```

The code is a `char`: each character of the request value is converted into a section. The comparison is case-sensitive.

#### 4.2.2. `ModelSelection`

```java
public final class ModelSelection {
    private final Set<ModelPart> parts;                    // unmodifiable EnumSet

    public static ModelSelection parse(String value) { ... } // InvalidModelSelectionException
    public boolean includesAny(ModelPart[] candidates) { ... }
    public Set<ModelPart> parts() { ... }
}
```

- Empty value → empty selection (valid).
- Repeated codes within the value → deduplicated.
- Unknown character → `InvalidModelSelectionException` (subclass of `IllegalArgumentException`, no HTTP dependencies).

#### 4.2.3. Jackson 3 module

```java
public class ModelFilteringModule extends SimpleModule {
    public static final String FILTER_ID = ModelFilteringModule.class.getName();

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.insertAnnotationIntrospector(new ModelFilteringAnnotationIntrospector());
    }
}
```

`ModelFilteringAnnotationIntrospector.findFilterId(config, annotated)`:
- Only acts on `AnnotatedClass`.
- Walks the bean's fields and methods (including the hierarchy). If none has marks, it returns `null` → bean untouched.
- If a member has both `@ModelBasic` and `@ModelSections` → `IllegalStateException` with the member name.
- If the bean has marks and also its own `@JsonFilter` → `IllegalStateException` (ambiguous configuration).
- Otherwise it returns `FILTER_ID`.

Since it is inserted as the primary introspector and Jackson caches serializers, the inspection is done only once per class and mapper.

`ModelSectionsPropertyFilter` (extends `SimpleBeanPropertyFilter`), immutable:
- Without selection → includes every property.
- With selection → includes `@ModelBasic`, empty `@ModelSections` and `@ModelSections` with some selected section (OR). Omits the rest, including those without a mark.
- Included properties are delegated to the original `BeanPropertyWriter`, so `NON_NULL`, `NON_ABSENT`, `@JsonIgnore`, `@JsonFormat`, views, etc. still apply.

`ModelFilterProvider` (extends `FilterProvider`), immutable:
- For `FILTER_ID` it returns its `ModelSectionsPropertyFilter`.
- For any other id it delegates to the `FilterProvider` that Boot's `JsonMapper` already had (if any), so as not to break the application's own `@JsonFilter`.

#### 4.2.4. `ModelFilteringAdvice`

```java
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {

    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // JacksonJsonHttpMessageConverter + resolved @ModelFiltering (method, then class)
    }

    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        // 1. Add the header name to Vary (without duplicating, honoring "Vary: *")
        // 2. If the response is already an error (status >= 400): do nothing else
        // 3. Read the query parameter; if absent, read the header; both absent → body unchanged
        //    (if there is a query parameter, the header is ignored and not validated)
        // 4. More than one value in the used source → ModelFilteringException (400)
        // 5. ModelSelection.parse(value); invalid code → ModelFilteringException (400)
        // 6. Store the selection as a request attribute
    }

    public Map<String, Object> determineWriteHints(Object body, MethodParameter returnType,
            MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType) {
        // If the current request has a selection: { FilterProvider.class.getName() → new ModelFilterProvider(selection, delegate) }
    }
}
```

The annotation is resolved with `AnnotatedElementUtils.findMergedAnnotation`, first on the method (including interfaces and overridden methods) and, if there is none, on the controller class (including superclasses and interfaces). The method annotation completely replaces the class one. The result is cached per method.

#### 4.2.5. `ModelFilteringAutoConfiguration`

```java
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ DispatcherServlet.class, JsonMapper.class })
@ConditionalOnBean(JsonMapper.class)
@ConditionalOnBooleanProperty("models.filtratge")
public class ModelFilteringAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)   // after Boot's customizer (@Order(0))
    ServerHttpMessageConvertersCustomizer modelFilteringConvertersCustomizer(JsonMapper jsonMapper) {
        // builder.withJsonConverter(new JacksonJsonHttpMessageConverter(filteringMapper(jsonMapper)))
    }

    @Bean
    ModelFilteringAdvice modelFilteringAdvice(JsonMapper jsonMapper) { ... }
}
```

The copy is made with `jsonMapper.rebuild().addModule(new ModelFilteringModule()).filterProvider(new ModelFilterProvider(null, original)).build()`, which keeps all of Boot's configuration (`spring.jackson.*`, registered modules, etc.).

### 4.3. Model Changes

Rule: in a marked bean, **every property without a mark is omitted** when there is a selection. That is why every basic property must be marked explicitly.

| Model | Marks |
|-------|---------|
| `VehicleModelBean` | `brand` → `BRAND`; `marketData` → `MARKET`; `parts` → `PARTS`; the rest `@ModelBasic` |
| `MarketDataBean` | `currency`, `localMarketPrice`, `marketPrice`, `margin` → `ECONOMIC`; the rest `@ModelBasic` |
| `VehiclePartBean` | `marketPrice`, `margin` → `ECONOMIC`; the rest `@ModelBasic` |
| `BrandBean` | No marks: always serialized in full when included |

All `@JsonFilter` annotations are removed.

### 4.4. Controller Changes

`@EnableModelFiltering` → `@ModelFiltering` on `VehicleController` and `BrandController`. `/brands` accepts valid codes, but since `BrandBean` has no marks, the response does not change.

### 4.5. Application Configuration

```yaml
models:
  filtratge: true
```

`@SpringBootApplication(scanBasePackages = "cat.albirar.dynmod.demo")`, so that the library is only loaded through auto-configuration.

The Jackson 2 configuration (`FilterConfiguration`) and the explicit `jackson-datatype-jdk8` and `jackson-datatype-jsr310` dependencies are removed: Jackson 3 already supports `Optional` and `java.time`, and `@JsonFormat` on `Instant` works the same.

---

## 5. Migration Plan

### 5.1. Phases

#### Phase 1: Library
- [x] `filtering.model` package: `ModelPart`, `ModelBasic`, `ModelSections`, `ModelSelection`
- [x] `filtering.jackson` package: module, introspector, filter and provider
- [x] `filtering.web` package: `ModelFiltering`, advice, exception, auto-configuration
- [x] Registration in `AutoConfiguration.imports`

#### Phase 2: Adaptation
- [x] Mark models and remove `@JsonFilter`
- [x] `@ModelFiltering` on the controllers
- [x] `models.filtratge=true` and `scanBasePackages` limited to the application

#### Phase 3: Removal of the old implementation
- [x] `FilterInterceptor`, `FilteredViewPropertyFilter`, `FilterContext`, `EnableModelFiltering`, `FilterConfiguration`, `FiltersModel`, `FilteredView` and the old `ModelFilteringException`
- [x] Tests of the old implementation (`FilterInterceptorTest`, `FilteredViewPropertyFilterTest`)
- [x] Explicit Jackson 2 dependencies

#### Phase 4: Tests
- [x] `ModelSelection`: valid and unknown codes, upper/lower case, duplicates, empty
- [x] Jackson module: basic, one section, OR, empty section, no mark, bean without marks, mark conflict, combined `@JsonFilter`, cascade to lists and maps, `NON_NULL`, `@JsonIgnore`, `Optional`/`Instant`, concurrency
- [x] Auto-configuration: no property, property false and true
- [x] Real Boot context + `MockMvc`: real endpoints, query/header, query parameter precedence, header ignored if there is a query parameter, custom names, absence, empty, repeated, no annotation, annotation on method/class/interface/superclass, replacement of the class one, `ResponseEntity`, lists, arrays, maps, `Page`, preserved errors, cumulative `Vary`

- [x] E2E against a real HTTP server (`ApiE2ETest`, tag `e2e`): the whole HTTP → MVC → JPA → H2 → JSON chain with the data from `data.sql`; sections and cascade, all sections = full model, header = query parameter, precedence, 400 errors, `Vary`, unannotated endpoints, search and pagination, concurrency
- [x] Smoke tests (`SmokeTest`, tag `smoke`) and `scripts/smoke-test.sh` for deployed environments

#### Phase 5: Validation
- [x] Start the application (packaged JAR) and run `scripts/smoke-test.sh`

### 5.2. API Changes

| **Aspect** | **Current** | **New** | **Impact** |
|-------------|------------|---------|-------------|
| **Code format** | `B,P,E` | `BPE` | ⚠️ **Breaking change** |
| **Case** | Insensitive | Sensitive | ⚠️ **Breaking change** (`b` is no longer valid) |
| **Header** | `x-filter-model` | `X-Model-Sections` | ⚠️ **Breaking change** |
| **Source** | Parameter > header | Parameter > header | ✅ No changes (the ignored header is no longer validated) |
| **Unmarked fields** | Always included | Omitted with selection if the bean has marks | ⚠️ Basic fields must be marked |
| **Property** | `spring.application.models.filtered` | `models.filtratge` | ⚠️ Configuration |

Since the current implementation returns 500 over HTTP, in practice no consumer can depend on the old contract.

### 5.3. Deployment Strategy

1. Deploy to development and validate.
2. Publish the contract (codes, query parameter and header per endpoint) in the API documentation.
3. Communicate the changes to consumers.
4. Validate in the test environment with consumers.
5. Deploy to production.

### 5.4. Rollback

- `models.filtratge=false` disables the whole infrastructure: endpoints return the full model and ignore `m`/`X-Model-Sections`. The model annotations have no effect without the module.
- The old implementation is removed; going back means reverting the code (and it would still not work on Boot 4).

---

## 6. Conclusion and Recommendation

### 6.1. Comparison Summary

| **Criterion** | **Current** | **Proposed** | **Winner** |
|-------------|------------|---------------|---------------|
| **Works on Boot 4** | ❌ HTTP 500 | ✅ | **Proposed** |
| **Safety** | ❌ ThreadLocal | ✅ No mutable state | **Proposed** |
| **Isolation** | ❌ Separate global mapper | ✅ Private mapper of the server converter | **Proposed** |
| **Robustness** | ❌ No mark validation | ✅ Strict validation | **Proposed** |
| **Standard** | ❌ Interceptor + ThreadLocal | ✅ ResponseBodyAdvice + hints | **Proposed** |
| **Simplicity** | ✅ Fewer components | ❌ More components | **Current** |

### 6.2. Final Recommendation

**✅ Implement the new architecture adapted to Spring Boot 4.** It is not just an improvement: the current implementation does not work with the project's Spring Boot version.

### 6.3. Alternatives Considered

1. **Fix the current one by configuring a `FilterProvider` on the Jackson 3 `JsonMapper`**: it would keep the `ThreadLocal`, the mandatory `@JsonFilter` and the global filter on the shared mapper.
2. **Subclass `JacksonJsonHttpMessageConverter` and pass the selection as an `ObjectWriter` attribute** (as literally described in the original document): `customizeWriter` does not receive the hints, so `writeInternal` would have to be overridden, duplicating Spring logic. Discarded in favor of the `FilterProvider` hint, which Spring already supports.
3. **`FilterProvider` hint + `AnnotationIntrospector`**: **Recommended and implemented**.

---

## Appendix A: Examples

### A.1. Without selection

**Request:** `GET /vehicles?size=1`
**Response:** full model, same as Jackson without filtering.

### A.2. With selection

**Request:** `GET /vehicles?brandManufacturerWMI=TOY&commercialNamePattern=Corolla&size=1&m=BP`
```json
{
  "content": [
    {
      "brand": {"commercialBrandName": "Toyota", "country": "JP", "manufacturer": "Toyota Motor Corporation", "manufacturerWMI": "TOY"},
      "commercialization": "1966-11-01T00:00:00.000Z",
      "construction": 1966,
      "inceptionYear": 1966,
      "line": "Corolla",
      "model": "Corolla",
      "parts": {
        "TRD-001": {"mountingReference": "Engine Bay", "name": "Engine Oil Filter", "partNumber": "TRD-001"},
        "TRD-002": {"mountingReference": "Front Wheels", "name": "Brake Pad Set", "partNumber": "TRD-002"}
      },
      "type": "Sedan"
    }
  ],
  "totalElements": 2
}
```
No `marketData` (`M` was not requested) and parts without prices (`E` was not requested).

### A.3. Error

**Request:** `GET /vehicles?m=BX`
**Response:** HTTP 400 — `Unknown model section code 'X' in 'BX'`.

---

## Appendix B: Glossary

| **Term** | **Definition** |
|----------|---------------|
| **ResponseBodyAdvice** | Spring MVC extension that runs before the response body is written |
| **Write hints** | Map of options that Spring 7 passes to the converter for a specific write (`determineWriteHints`) |
| **FilterProvider** | Jackson property filter provider, applicable per `ObjectWriter` |
| **AnnotationIntrospector** | Jackson component that interprets annotations; here it assigns the filter to marked beans |
| **Vary Header** | HTTP header that tells caches which request headers affect the response |
| **ModelBasic** | Property always included in filtered responses |
| **ModelSections** | Property associated with one or more optional sections |
| **ModelFiltering** | Opt-in of an endpoint or controller |

---

## Appendix C: References

- [Original Architecture Document](./arquitectura-models-dinamics.en.md)
- [Spring Boot 4.1 Reference](https://docs.spring.io/spring-boot/4.1/reference/)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Spring Framework `ResponseBodyAdvice`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html)
