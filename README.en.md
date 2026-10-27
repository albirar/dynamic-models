# Dynamic models in REST APIs

[Català](README.md) · **English** · [Español](README.es.md)

Proof of concept of a REST API in which **the client decides which sections of the model it wants to receive**, using compact codes sent as a query parameter or a header:

```http
GET /vehicles?m=BP              → basic data + brand (B) + parts (P)
GET /vehicles                   → full model, like any other API
X-Model-Sections: M             → basic data + market data, via header
```

The service keeps **a single response model** per resource. The selection is applied during JSON serialization, without duplicating DTOs, endpoints or queries.

Stack: Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3), Spring Data JPA, H2.

---

## Why

Most REST APIs always return the whole model. It is simple, but as models grow some well-known problems appear:

- **Over-fetching**: a list screen that only shows name and type also receives prices, markets and parts for every item.
- **Proliferation of endpoints or DTOs**: to avoid it, variants are created (`/vehicles/summary`, `VehicleLightDto`, `VehicleDetailDto`...) that must be maintained, documented and versioned separately.
- **Network and parsing cost**: relevant for mobile clients, for traffic between data centers and for public APIs with egress costs.

This project proposes a middle ground between "always everything" and a full query language such as GraphQL: the model is split into **named sections** that the client can request or omit.

## How it works

### 1. Mark the model

```java
public class VehicleModelBean {
    @ModelBasic                              // always included
    private String model;

    @ModelSections(ModelPart.BRAND)          // included if B is requested
    private BrandBean brand;

    @ModelSections(ModelPart.MARKET)         // included if M is requested
    private Map<String, MarketDataBean> marketData;
}

public class MarketDataBean {
    @ModelBasic
    private String country;

    @ModelSections(ModelPart.ECONOMIC)       // prices only if E is requested
    private float marketPrice;
}
```

### 2. Enable it on the endpoint

```java
@RestController
@RequestMapping("/vehicles")
@ModelFiltering
public class VehicleController { ... }
```

```yaml
models:
  filtratge: true
```

### 3. Request sections from the client

| Code | Section | Content |
| --- | --- | --- |
| `B` | `BRAND` | Vehicle brand |
| `M` | `MARKET` | Marketing data per country |
| `P` | `PARTS` | Vehicle parts |
| `E` | `ECONOMIC` | Prices and margins (inside markets and parts) |

| Request | Result |
| --- | --- |
| No `m` and no header | Full model (usual behavior) |
| `?m=` (empty) | Basic data only |
| `?m=BP` | Basic + brand + parts, without prices |
| `?m=PE` | Basic + parts with prices |
| `X-Model-Sections: BP` | Same as `?m=BP` |
| `?m=M` + `X-Model-Sections: B` | The query parameter takes precedence: only `M` |
| `?m=BX`, `?m=b`, `?m=B,P`, `?m=B&m=P` | HTTP 400 |

Rules:

- Codes are **concatenated**, case-sensitive, and repeated codes are ignored (`BBP` = `BP`).
- The selection **cascades**: `E` affects the prices of markets and parts, but only if `M` or `P` are present. A child cannot make an excluded parent appear.
- If the bean has any mark, properties **without a mark are omitted** when there is a selection. A bean without any mark (for example `BrandBean` or the pagination wrappers) is always serialized in full.
- Header and query parameter can be used interchangeably. **Only when both are present**, the query parameter wins and the header is ignored without being validated. This lets a client set a default selection in the header and override it in specific requests.
- Filtered endpoints always respond with `Vary: X-Model-Sections`, so that HTTP caches do not mix responses.
- The usual Jackson rules (`@JsonInclude`, `@JsonIgnore`, `@JsonFormat`, views, custom filters) still apply.

### Real example

With the 25 vehicle records of the demo (`GET /vehicles?size=100`):

| Selection | Response size |
| --- | --- |
| Full model | 7,817 bytes |
| `m=B` | 6,156 bytes (−21 %) |
| `m=` | 3,234 bytes (−59 %) |

In real models, with large collections (histories, lines, documents), the difference is usually much bigger.

---

## Applying it to a production REST API

### Where it adds value

- **Lists and details on the same resource.** A list requests `m=` and the detail view requests `m=BMP`, without maintaining two DTOs or two endpoints.
- **Heterogeneous clients.** The mobile app, the web and third-party integrations consume the same endpoint with different selections.
- **Data sensitive to transfer cost.** Mobile networks, billed cloud egress, cross-region traffic.
- **Backends for Frontends and gateways** that aggregate several APIs and only need part of each response.
- **Evolving the model without breaking clients.** Adding a new section does not change the response for clients that already send a selection. Those that do not send one will receive the new field, as in any API.

### How to roll it out

1. **Gradual opt-in.** The infrastructure is enabled with `models.filtratge=true` and each endpoint with `@ModelFiltering`. Without a selection the response is identical to the previous one, so existing clients do not notice.
2. **Publish the contract.** The annotation does not generate OpenAPI by itself. For each endpoint, document the query parameter, the header and the available sections with their codes. Codes are part of the contract: they cannot be reused or change meaning.
3. **Caches and CDN.** The query parameter is usually already part of the cache key. For the header, check that the CDN or proxy honors `Vary` (or add the header to the cache key explicitly).
4. **CORS.** If a browser has to send `X-Model-Sections`, add it to `Access-Control-Allow-Headers`.
5. **Observability.** Monitor 400 errors caused by invalid selections (misconfigured clients) and response size per selection, to confirm the benefit.
6. **Smoke tests after every deployment.** `scripts/smoke-test.sh <URL>` checks that the API responds and that filtering is active.

### What it is not

- **It is not an authorization mechanism.** Without a selection the full model is returned; everything the model can contain must already be authorized for the user. To hide sensitive data, filter it beforehand, in the service layer.
- **It is not a query projection.** Filtering happens at serialization time: the database and the DTO mapping do the same work with or without a selection.

---

## Pros and cons compared with always returning the full model

### Pros

| | |
| --- | --- |
| **Less data transferred** | Clients receive only what they use. Lower latency, lower network cost and less parsing on the client. |
| **A single model and a single endpoint** | Avoids the proliferation of "light", "summary" and "detail" DTOs and of parallel endpoints that must be kept in sync. |
| **Compatible with existing clients** | Without a selection the behavior is exactly the same as before. It can be adopted endpoint by endpoint and client by client. |
| **Simple contract** | One-letter codes, easy to send from any HTTP client, without a query language or a special client library. |
| **Stable, business-meaningful sections** | The client asks for "market data", not for a list of specific fields. The server can reorganize fields inside a section without breaking the contract. |
| **Compatible with HTTP caches** | Responses are normal GETs with `Vary`; no need to change infrastructure as would happen with GraphQL over POST. |
| **Strict validation** | An unknown code is an explicit 400, not a silently incomplete response. |

### Cons

| | |
| --- | --- |
| **Does not save work on the server** | Data is loaded and mapped anyway. If the main cost is the database or a remote service, combine it with projections or lazy loading per section. |
| **More complex contract** | Every field of the model may or may not be present depending on the request. Typed clients must treat every non-basic field as optional, and the documentation must explain the sections. |
| **OpenAPI cannot express it** | Client generation tools do not know which fields depend on each section; it must be documented by hand or with custom extensions. |
| **Cache fragmentation** | Each combination of sections is a different entry. With 4 sections there are up to 16 variants per URL. |
| **Limited granularity** | Sections are coarse-grained: a single field cannot be requested. The same selection applies to every level of the model. |
| **Finite code space** | One letter per section. Enough for dozens of sections, but they must be governed and cannot be recycled. |
| **Requires discipline in DTOs** | Once a bean is marked, every new field without a mark disappears from filtered responses. Contract tests must guard against this regression. |
| **More combinations to test** | Behavior depends on the selection; tests must cover at least the full model, the basic one and each section. |

### Compared with other alternatives

| Alternative | Comparison |
| --- | --- |
| **Always the full model** | Simpler, more predictable and better supported by tools; acceptable while models are small or clients are homogeneous. |
| **Specific endpoints or DTOs** | Explicit, typed contract, but multiplies code and documentation with every new client need. |
| **Sparse fieldsets** (`fields=a,b` in JSON:API or Google's partial responses) | Per-field granularity, more flexible but more verbose, harder to cache, and couples the client to internal field names. |
| **GraphQL** | Arbitrary, typed selection, and it also solves data loading. Requires another stack, another cache model and another contract. |

**When to use it:** models with clearly separable data blocks, clients with different needs on the same resource, and a REST API that you want to keep. **When not to:** small models, a single client, or when the real cost lies in the database rather than in the response.

---

## Architecture

```
client ─HTTP─► Spring MVC ─► controller ─► service ─► JPA
                   │
                   ▼
         ModelFilteringAdvice        reads ?m or X-Model-Sections, validates (400), adds Vary
                   │                 and passes the selection as a write hint
                   ▼
  JacksonJsonHttpMessageConverter    private copy of Boot's JsonMapper + ModelFilteringModule
                   │
                   ▼
  Per-response PropertyFilter        includes @ModelBasic and the selected @ModelSections,
                                     cascading through the whole graph
```

- **No shared state**: the selection is an immutable per-response object; there is no `ThreadLocal`.
- **Isolated**: only the server JSON converter uses the mapper with the module; the application's `JsonMapper` and the HTTP clients are unchanged.
- **Infrastructure as a library**: the `cat.albirar.dynmod.filtering` package does not depend on the application and is loaded through auto-configuration.

Details in:

- [Model filtering architecture](src/main/docs/arquitectura-models-dinamics.en.md): contract and design.
- [New implementation](src/main/docs/nova-implementacio.en.md): adaptation to Spring Boot 4 / Jackson 3 and migration from the previous version.

### Structure

```
src/main/java/cat/albirar/dynmod/
├── filtering/                    reusable infrastructure
│   ├── model/                    ModelPart, @ModelBasic, @ModelSections, ModelSelection
│   ├── jackson/                  module, introspector, filter and filter provider
│   └── web/                      @ModelFiltering, advice, auto-configuration
└── demo/                         sample application (vehicles and brands)
scripts/smoke-test.sh             smoke tests against a deployed instance
```

---

## Running

Requirements: JDK 21 and Maven.

```bash
mvn spring-boot:run
```

| URL | |
| --- | --- |
| `http://localhost:8080/vehicles?m=BP` | Vehicles with brand and parts |
| `http://localhost:8080/brands` | Brands |
| `http://localhost:8080/swagger-ui.html` | OpenAPI documentation |

Search parameters for `/vehicles`: `brandManufacturerWMI`, `country`, `type`, `commercialNamePattern`, and standard pagination (`page`, `size`, `sort`).

## Tests

```bash
mvn test                        # all: unit, integration, E2E and smoke
mvn test -Dgroups=e2e           # E2E against a real HTTP server
mvn test -Dgroups=smoke         # smoke tests
scripts/smoke-test.sh http://localhost:8080   # smoke tests against a running instance
```

| Level | What it covers |
| --- | --- |
| Unit | Selection parser; Jackson module (marks, cascade, invalid configurations, concurrency) |
| Integration | Auto-configuration conditions; real Boot context with `MockMvc` (response shapes, header and query parameter, precedence, `Vary`, errors, annotation inheritance) |
| E2E | Real HTTP server with the data from `data.sql`: sections and cascade, all sections equal the full model, header equivalent to the query parameter, precedence, 400 errors, search and pagination, concurrent requests |
| Smoke | The application starts, the main endpoints respond, filtering is active and OpenAPI is published |
