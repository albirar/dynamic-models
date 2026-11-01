# Projections: from filtering the response to reducing the database load

[Català](projeccions.md) · **English** · [Español](projeccions.es.md)

## Table of Contents
1. [Introduction](#1-introduction)
2. [Starting point](#2-starting-point)
3. [Design principle](#3-design-principle)
4. [Phase 0: selection available before the controller](#4-phase-0-selection-available-before-the-controller) ✅ implemented
5. [Phase 1: projection by associations](#5-phase-1-projection-by-associations) ✅ implemented
6. [Phase 2: projection by columns](#6-phase-2-projection-by-columns) ✅ implemented
7. [Phase 3: projection plan derived from the annotations](#7-phase-3-projection-plan-derived-from-the-annotations) ✅ implemented
8. [Testing strategy](#8-testing-strategy)
9. [Summary and recommendation](#9-summary-and-recommendation)

---

## 1. Introduction

### 1.1. Context

The current model filtering ([arquitectura-models-dinamics.en.md](./arquitectura-models-dinamics.en.md)) lets the client choose which sections of the model it wants to receive (`?m=BP`, `X-Model-Sections: M`). The selection is applied **at serialization time**: it reduces the network load, but the database and the DTO mapping do exactly the same work with or without a selection.

### 1.2. Goal

Turn the client's selection into a **database projection**: read only the associations and columns that belong to the requested sections, without changing the API contract.

---

## 2. Starting point

### 2.1. Current flow

```
Controller ─► Service ─► JPA (whole entities) ─► Converter (copies everything) ─► Jackson + filter (discards)
                                                                                     ▲
                                              ModelFilteringAdvice.beforeBodyWrite ──┘  (reads ?m here)
```

### 2.2. Measurement

SQL statements of `GET /vehicles?size=100` with the 25 demo vehicles, counted on the running application before applying any phase:

| Selection | SQL queries |
| --- | --- |
| Full model | 61 |
| `m=` | 61 |
| `m=B` | 61 |
| `m=BMPE` | 61 |
| `m=BX` (invalid → 400) | 61 |

### 2.3. Diagnosis

1. **The selection arrived too late.** It was read in `ModelFilteringAdvice.beforeBodyWrite`, when the controller had already queried the database. To project, the selection must be known before calling the service. Moreover, an invalid selection ran every query and then returned 400.
2. **N+1 problem triggered by the converter.** `brand`, `marketData` and `parts` are lazy associations. `VehicleEntityToVehicleModelBeanConverter` walks all of them and, with `spring.jpa.open-in-view` enabled by default, each access fires a query: 1 (page) + distinct brands + 25 (markets) + 25 (parts).
3. **Section `E` (ECONOMIC) is column-level.** `market_price`, `margin`, `currency`... live in the same tables as the basic data. Hibernate does not load partial entities, so saving these columns requires tuple or DTO queries, not entity queries.

---

## 3. Design principle

> **The projection is an optimization. The Jackson filter remains the guarantor of the contract.**

| Layer | Responsibility |
| --- | --- |
| Projection (data) | Do not read what was not requested |
| Jackson filter (serialization) | Decide the shape of the JSON |

The serialization filter is essential even with a projection. For example, `float marketPrice` is a primitive: if it is not loaded it is `0.0`, and without the filter it would appear in the response instead of being absent.

Consequences:

- An incomplete or too broad projection **never changes the response**, only the cost.
- The existing E2E tests validate the projection unchanged: responses must be identical.
- The projection can be adopted endpoint by endpoint, like filtering.

---

## 4. Phase 0: selection available before the controller

**Status: implemented on the `feature/projeccio` branch.**

### 4.1. Goal

Have the selection resolved and validated before the controller runs, and let the data layer receive it as an object without `null`.

### 4.2. Changes to `ModelSelection`

| Method | Description |
| --- | --- |
| `ModelSelection.full()` | Selection of a request without `m` or header: the full model. Replaces the use of `null`. |
| `isFull()` | `true` only for the full selection |
| `includes(ModelPart)` | The section is selected |
| `includesAll(ModelPart...)` | Every section is selected. Expresses the cascade: `includesAll(MARKET, ECONOMIC)` |

`full()` is **not** equivalent to `parse("BMPE")`: the full selection also writes unmarked properties, while a selection with every code omits them. That is why both instances are not equal (`equals`).

`ModelFilterProvider` and `ModelSectionsPropertyFilter` still accept `null` as a synonym of `full()`, for compatibility.

### 4.3. New components in `cat.albirar.dynmod.filtering.web`

```
request ─► ModelSelectionInterceptor.preHandle ──► ModelSelectionResolver ──► request attribute
              (400 here if invalid)                        ▲                       │
                                                           │                       ▼
controller(…, ModelSelection selection) ◄── ModelSelectionArgumentResolver ◄───────┤
                                                                                   ▼
                                                      ModelFilteringAdvice (Jackson filter)
```

| Class | Responsibility |
| --- | --- |
| `ModelSelectionResolver` | Finds the endpoint's `@ModelFiltering` (cached) and resolves the selection: query parameter, or header if the parameter is absent. Stores it in the `SELECTION_ATTRIBUTE` attribute so it is parsed only once. |
| `ModelSelectionInterceptor` | `HandlerInterceptor` that resolves the selection of each annotated endpoint before the handler. An invalid selection returns 400 (with `Vary`) without invoking the controller. |
| `ModelSelectionArgumentResolver` | Injects the selection into any parameter of type `ModelSelection`. Endpoints without `@ModelFiltering` receive `full()`, since their response is never filtered. |
| `ModelFilteringAdvice` | No longer parses. Reuses the resolved selection (or resolves it if the interceptor is not registered) and keeps applying the Jackson filter and `Vary`. |

The auto-configuration registers the resolver as a bean and adds the interceptor and the argument resolver through a `WebMvcConfigurer`, under the same `models.filtratge=true` property.

### 4.4. Usage from a controller

```java
@GetMapping
public PagedResponse<VehicleModelBean> searchVehicles(..., ModelSelection selection, Pageable pageable) {
    Page<VehicleEntity> page = vehicleService.searchVehicles(criteria, selection, pageable);
    ...
}
```

### 4.5. Behaviour changes

| Case | Before | Now |
| --- | --- | --- |
| `?m=BX` | The controller runs (61 queries), then 400 | 400 before the controller (0 queries) |
| Invalid selection on an endpoint that returns an error (e.g. 409) | Unfiltered 409 | 400: the request is invalid before reaching the handler |
| Other cases | — | Unchanged: every existing test passes |

### 4.6. Added tests

- `ModelSelectionTest`: `full()`, `isFull()`, `includes`, `includesAll`, and that `full()` is not equal to every code.
- `ModelFilteringWebTest`: the handler receives the selection (query, header, empty, full, custom names), an invalid selection does not invoke the controller and returns `Vary`, and an endpoint without annotation receives `full()`.
- `ModelFilteringAutoConfigurationTest`: the `ModelSelectionResolver` is registered.

---

## 5. Phase 1: projection by associations

**Status: implemented on the `feature/projeccio` branch.** Phase 2 replaced it in `/vehicles`; it is kept as an entity-based alternative and as the reference for the equivalence test. The controller description in this section refers to phase 1.

**Goal:** load only the associations of the selected sections (`B`, `M`, `P`) and remove the N+1 problem.

### 5.1. Selection-aware service

`VehicleService.searchVehicles(criteria, selection, pageable)`, inside a read-only transaction:

1. **Paged root query** with the existing `Specification`s. If `selection.includes(BRAND)`, `VehicleSpecifications.fetchBrand()` is added, which does `root.fetch("brand")`. A `ManyToOne` does not affect pagination.
2. **If `includes(MARKET)`:** a single query for the whole page, which initializes the collection of the vehicles already loaded in the persistence context:
   ```java
   @Query("select v from VehicleEntity v left join fetch v.marketData where v.id in :ids")
   List<VehicleEntity> fetchMarketData(@Param("ids") Collection<Long> ids);
   ```
3. **If `includes(PARTS)`:** the same with `fetchParts`.

The result of `fetchMarketData` and `fetchParts` is not used directly. The query returns the same instances already in the persistence context, with the collection initialized. This avoids replacing collections managed by Hibernate (with `orphanRemoval = true`, that would make the flush fail).

The previous signature `searchVehicles(criteria, pageable)` is kept and is equivalent to the full selection.

### 5.2. Why not `fetch join` the collections in the paged query

- With pagination, Hibernate applies `firstResult`/`maxResults` in memory (warning HHH90003004): it reads every row and discards.
- Fetching two `List`s at once (`marketData` and `parts`) raises `MultipleBagFetchException`.

One `IN (:ids)` query per collection gives a constant number of queries and keeps pagination in the database.

### 5.3. The pagination `count`

The `Specification` is also applied to the `count` query, and a `count` query cannot fetch (the entity is not in the `select`). `fetchBrand()` detects this case through the result type (`Long`) and does not add the fetch:

```java
public static Specification<VehicleEntity> fetchBrand() {
    return (root, query, cb) -> {
        if (!Long.class.equals(query.getResultType())) {
            root.fetch("brand");
        }
        return null;
    };
}
```

### 5.4. Selection-aware converter

`VehicleEntityToVehicleModelBeanConverter` has a new method, `convert(entity, selection)`, that only walks the associations of the selected sections. `convert(entity)` still exists (it is the Spring `Converter`) and is equivalent to the full selection.

`VehicleController` receives the selection as a parameter (phase 0), passes it to the service and the converter, and depends directly on the converter instead of the `ConversionService`, which does not accept additional context.

The latent `Currency.getInstance(null)` bug when a market has no currency has also been fixed.

### 5.5. Open Session in View disabled

```yaml
spring:
  jpa:
    open-in-view: false
```

With OSIV disabled, the controller's conversion runs outside the session. Any access to an association that was not loaded throws `LazyInitializationException` instead of a silent query. This is the safety net that guarantees the projection works.

### 5.6. Measured result

`GET /vehicles?size=100`, 25 vehicles in a single page (Spring Data skips the `count` because the page is not full):

| Selection | Before | Now | Response size |
| --- | --- | --- | --- |
| `m=` / `m=E` | 61 | 1 | 3,234 bytes |
| `m=B` / `m=BE` | 61 | 1 | 6,156 bytes |
| `m=M` / `m=P` / `m=PE` / `m=BM` | 61 | 2 | — |
| `m=MP` / `m=BMPE` / full model | 61 | 3 | 7,817 bytes |
| `m=BX` (invalid) | 61 | 0 | 400 |

Response sizes are identical to before: the projection does not change the contract. A page that needs a `count` (`?size=5&page=1&m=B`) runs 2 queries.

### 5.7. Added tests

| Test | What it verifies |
| --- | --- |
| `VehicleProjectionTest` | Using Hibernate statistics, the number of statements for each selection in the table above; that an invalid selection runs no query; and that search filters on brand and markets work whether those sections are selected or not. With OSIV disabled, an unexpected lazy access would fail the request. |
| `VehicleServiceTest` | Collections are loaded only if the section is selected, with the page ids, and never for an empty page. |
| `VehicleEntityToVehicleModelBeanConverterTest` | The converter does not access associations of unselected sections and accepts markets without currency. |

The existing E2E tests (including "every section equals the full model") pass unchanged.

### 5.8. Known limitations

- ~~**Lombok `@Data` on entities.**~~ **Resolved.** `equals`, `hashCode` and `toString` traversed lazy associations and failed outside the session. Entities now use `@Getter`/`@Setter`, `@ToString` with `@ToString.Exclude` on associations, and an identifier-based `equals` with a constant `hashCode` (it does not change when the id is assigned). `VehicleProjectionTest` verifies it with entities detached from the session.
- **Double join on `brands`.** When filtering by brand and also selecting `B`, the query has one `join` for the filter and another for the fetch. The result is correct; reusing the same join would be an optimization.
- **Columns.** Loaded entities carry every column, including the `ECONOMIC` ones. That is the scope of phase 2.

---

## 6. Phase 2: projection by columns

**Status: implemented on the `feature/projeccio` branch. It is the path used by `/vehicles`.**

**Goal:** read only the columns of the selected sections, including `E`, without loading entities.

### 6.1. Generic engine: `cat.albirar.dynmod.filtering.jpa`

| Class | Responsibility |
| --- | --- |
| `ProjectionPlan<T>` | Declares, for one resource, the identifier, the basic columns, the columns of each section (also through `ManyToOne` associations, e.g. `brand.country`) and the collections with their own columns and subsections. |
| `ProjectionColumns` | Basic and per-section columns of one level. Computes which ones to read for a selection, applying the cascade (`ECONOMIC` inside `MARKET` only with `M` and `E`). |
| `ProjectionExecutor` | Builds and runs the tuple queries with the Criteria API. Returns a `Page<ProjectedRow>`. |
| `ProjectedRow` | Values read for one row, by path. `has(path)` and `hasCollection(name)` tell what was read; a read path may be `null`. |

The package does not depend on the application. It depends on JPA and Spring Data JPA (`Specification`, `Pageable`) and is not auto-configured: the application declares the `ProjectionExecutor` bean.

### 6.2. Plan of `/vehicles`

`VehicleProjection` held the plan and the conversion to `VehicleModelBean`. Since phase 3 the plan is no longer written by hand: it is derived from the bean annotations (section 7) and is identical to this one:

```java
public static final ProjectionPlan<VehicleEntity> PLAN = ProjectionPlan.of(VehicleEntity.class, "id")
        .basic("line", "model", "type", "inceptionYear", "constructionYear", "commercialization", "retirement")
        .section(BRAND, "brand.manufacturerWMI", "brand.commercialBrandName", "brand.manufacturer", "brand.country")
        .collection(MARKET, "marketData", market -> market
                .basic("country", "startCommercializing", "endCommercializing", "commercialName")
                .section(ECONOMIC, "currency", "localMarketPrice", "marketPrice", "margin"))
        .collection(PARTS, "parts", part -> part
                .basic("partNumber", "name", "mountingReference")
                .section(ECONOMIC, "marketPrice", "margin"));
```

### 6.3. Generated queries

1. **Paged root query:** identifier + basic columns + columns of the selected `ManyToOne` sections, with one `left join` per association (shared between paths). The existing `Specification`s apply unchanged through `spec.toPredicate(root, query, cb)`.
2. **One query per selected collection:** `from VehicleEntity v join v.marketData m where v.id in (:ids)`, with the owner identifier and the element columns. Neither the child entity nor its back reference needs to be known.
3. **`count`**, only when Spring Data needs it (`PageableExecutionUtils`), with the same rule as `JpaSpecificationExecutor`: `count(distinct)` if a filter marked the query as `distinct`.

Real example for `?m=BM&commercialNamePattern=a&sort=model,desc&page=1&size=5`:

```sql
select distinct ve1_0.id, ve1_0.line, ve1_0.model, ve1_0.type, ve1_0.inception_year, ve1_0.construction_year,
       ve1_0.commercialization, ve1_0.retirement,
       b1_0.manufacturer_wmi, b1_0.commercial_brand_name, b1_0.manufacturer, b1_0.country
from vehicles ve1_0
join vehicle_market_data md1_0 on ve1_0.id = md1_0.vehicle_id
left join brands b1_0 on b1_0.manufacturer_wmi = ve1_0.brand_wmi
where lower(md1_0.commercial_name) like ? escape ''
order by 3 desc offset ? rows fetch first ? rows only
```

**`distinct` with tuples.** The initial concern is resolved: the root query only contains columns of the root and of `ManyToOne` associations, which depend functionally on the identifier. Therefore `distinct` on these columns is equivalent to `distinct` on the entity. Sort expressions are added to the `select`, as the database requires with `DISTINCT`.

### 6.4. From rows to beans

`VehicleProjection.toBean(row)` builds the bean from the projected values:

- The brand is built only if `row.has("brand.manufacturerWMI")`; collections only if `row.hasCollection(...)`.
- An empty collection stays `null`, as the entity converter did, so the response is identical.
- Properties that were not projected (e.g. prices without `E`) keep their default value, and the Jackson filter omits them. This is the design principle of section 3.

### 6.5. Integration

- `VehicleService.searchVehicleModels(criteria, selection, pageable)` runs the plan and returns `Page<VehicleModelBean>`.
- `VehicleController` uses it and no longer depends on the converter.
- The phase 1 path (`searchVehicles(criteria, selection, pageable)` + `convert(entity, selection)`) is kept as an entity-based alternative and as the reference for the equivalence test.

### 6.6. Measured result

`GET /vehicles?size=100`:

| Selection | Queries | Tables and columns read | Response size |
| --- | --- | --- | --- |
| `m=` | 1 | `vehicles` (basic columns) | 3,234 bytes |
| `m=B` | 1 | `vehicles` + `brands` (join) | 6,156 bytes |
| `m=M` | 2 | + `vehicle_market_data` without `currency`, prices or `margin` | 3,896 bytes |
| `m=ME` | 2 | + `vehicle_market_data` with the economic columns | 4,379 bytes |
| `m=BMPE` / full model | 3 | all | 7,817 bytes |
| `m=BX` | 0 | — | 400 |

The number of queries is the same as in phase 1, but now no query reads columns of unselected sections, and no entity or persistence context is created.

### 6.7. Added tests

| Test | What it verifies |
| --- | --- |
| `ProjectionColumnsTest` | Columns per selection, cascade of nested sections and repeated paths. |
| `ProjectedRowTest` | Difference between a `null` value and a path that was not read; collections present only if read. |
| `VehicleProjectionTest` | With a Hibernate `StatementInspector` that records the SQL: `m=` only reads `vehicles`; `m=B` joins `brands` in the same query; `m=MP` does not read `currency`, `market_price` or `margin`; `m=ME` does; `m=PE` does not touch `vehicle_market_data`. |
| `VehicleProjectionEquivalenceTest` | 80 cases (10 selections × 8 searches with filters, pages, sorting and `distinct`): the filtered JSON of phase 2 is identical to that of phase 1, and the totals match. |

### 6.8. Known limitations

- **Sorting.** Only by root paths or `ManyToOne` association paths. Sorting by a collection path would duplicate rows.
- ~~**Plan validation.**~~ **Resolved** (section 6.9): the plan is validated against the JPA metamodel at startup.
- ~~**Duplicated plan and annotations.**~~ **Resolved** by phase 3 (section 7).
- **Page size.** Collections are read with `in (:ids)`. With very large pages, the list should be split.
- **Types.** `ProjectedRow.get` performs an unchecked cast; a type error in the assembler shows up at run time.

### 6.9. Plan validation

`ProjectionExecutor.validate(plan)` checks the plan against the JPA metamodel and reports **every** problem at once with an `InvalidProjectionPlanException`. The rules are the ones the executor relies on:

- The root type is an entity, and the identifier exists on it.
- Every path ends in a basic attribute. Intermediate segments can only be `ManyToOne` or `OneToOne` associations.
- Every collection is a plural attribute of the root with entity or embeddable elements, and its paths follow the rules above from the element type.

Each plan is validated once. `findAll` validates the plan before the first query, and the demo moves this to startup when creating the bean:

```java
@Bean
ProjectionExecutor projectionExecutor(EntityManager entityManager) {
    ProjectionExecutor executor = new ProjectionExecutor(entityManager);
    // Since phase 3: derives the plan from the beans and validates it
    VehicleProjection.plan(executor);
    return executor;
}
```

With a typo (`brand.contry`), the application does not start:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - unknown attribute 'contry' of BrandEntity in path 'brand.contry'
```

`ProjectionPlanValidatorTest` covers the demo plan, a type that is not an entity, unknown attributes and identifier, paths ending in an association, paths crossing collections or basic attributes, collections that do not exist or are not collections, and that the executor does not query with an invalid plan.

---

## 7. Phase 3: projection plan derived from the annotations

**Status: implemented on the `feature/projeccio` branch.**

**Goal:** not maintaining the `ProjectionPlan` by hand. The projection sections come from the same annotations the serialization uses, so they cannot diverge.

### 7.1. Usage

The entity attribute only has to be given when its name differs from the property name:

```java
@ModelBasic
@ModelSource("constructionYear")
private int construction;
```

The executor derives the plan, validates it and keeps it (once per model–entity pair):

```java
ProjectionPlan<VehicleEntity> plan = executor.planFor(VehicleModelBean.class, VehicleEntity.class);
```

`VehicleProjection.plan(executor)` wraps this call. The demo configuration makes it when creating the `ProjectionExecutor` bean, so a model that does not fit the entities stops the startup. For example, if the `@ModelSource` above is removed:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - property VehicleModelBean.construction: unknown attribute 'construction' of VehicleEntity (use @ModelSource if the name differs)
```

`ProjectionPlan.fromModel(metamodel, model, entity)` returns the plan without validation, for use outside the executor.

### 7.2. Derivation rules

**Section of each property** (the same rules as the serialization filter):

| Property | Read |
| --- | --- |
| `@ModelBasic`, or `@ModelSections` without sections | Whenever the enclosing model is read |
| `@ModelSections(X, Y)` | If `X` or `Y` is selected, together with the sections of the enclosing properties |
| Unmarked, in a model with marks | Only with the full selection |
| Any property of a model without marks | Whenever the model is read |

**Type of the source attribute** (`@ModelSource` or the property name):

| Entity attribute | Result |
| --- | --- |
| Basic | One column |
| `ManyToOne`, `OneToOne` or `Embedded` | The columns of the nested model (also inside an `Optional`), with the association prefix |
| Collection | A collection of the plan. The element is the value of a `Map`, the element of a `Collection` or of an array |

Properties with `@JsonIgnore`, `static` or `transient` are ignored. Annotations are looked up on the field and, if absent, on the getter, as the serialization does.

### 7.3. Generalization of `ProjectionColumns`

To represent any model, `ProjectionColumns` now supports:

- **`sections(List.of(BRAND, ECONOMIC), ...)`**: columns read only if every section is selected. This is the case of a section inside a model nested through a `ManyToOne` association.
- **`fullOnly(...)`**: columns read only with the full selection, for unmarked properties of a marked model.

`ProjectionPlan.toString()` and `ProjectionColumns.toString()` describe the plan in canonical order. Two equivalent plans are described the same way, which lets tests compare them.

### 7.4. Added tests

| Test | What it verifies |
| --- | --- |
| `ModelPlanDeriverTest` | The plan derived from `VehicleModelBean` is identical to the one written by hand in phase 2. It also covers: `@ModelSource`, nested models with and without marks, `Optional`, properties in two sections, unmarked properties, `@JsonIgnore`, `Map` and `List` as collections, and the errors (unknown attributes, sources crossing collections, collections without a single section, unknown element, nested collections, type that is not an entity). Also that the executor derives each plan only once and validates it. |
| `ProjectionColumnsTest` | Nested sections, full-selection-only columns and canonical description. |

The phase 2 equivalence and SQL tests pass unchanged with the derived plan.

### 7.5. Scope and limitations

- **The assembler is still manual.** `VehicleProjection.toBean` converts the values into beans. The shape differences between entity and bean (`List` → keyed `Map`, `String` → `Currency`, duplicated enums, `Optional`) make automating it close to a mini-ORM. Phase 3 automates what could diverge from the annotations (which columns are read for each section) and keeps the conversion explicit.
- **Collections.** Only as direct properties of the root model, and in a single section, because the executor reads them with one query per collection from the root.
- **Computed properties.** A property without an entity attribute is a derivation error. If needed, mark it with `@JsonIgnore` or compute it outside the projected model.
- **Field-based discovery.** Properties are discovered through the fields of the model and its superclasses. A model without fields (getters only) cannot be derived.

---

## 8. Testing strategy

| Type | What it verifies | Status |
| --- | --- | --- |
| **Equivalence** | The existing E2E tests pass unchanged and responses are identical to those before the projection. | ✅ Phase 1 |
| **Cost** | With `hibernate.generate_statistics=true`, the number of statements per selection matches the phase 1 table. | ✅ Phase 1 (`VehicleProjectionTest`) |
| **Guardrail** | With `open-in-view=false`, no test triggers `LazyInitializationException`. | ✅ Phase 1 |
| **Column absence** | The SQL of each selection contains no tables or columns of unselected sections. | ✅ Phase 2 (`VehicleProjectionTest`) |
| **Equivalence between phases** | Column projection and entity loading produce the same filtered JSON. | ✅ Phase 2 (`VehicleProjectionEquivalenceTest`) |

---

## 9. Summary and recommendation

| Phase | Scope | Gain | Complexity | Status |
| --- | --- | --- | --- | --- |
| 0 | Selection resolved before the controller | Validation without touching the DB; foundation for the rest | Low | ✅ Implemented |
| 1 | Associations per section, no N+1 | 61 → 1–3 queries | Medium | ✅ Implemented |
| 2 | Columns per section (including `E`) | Less data read per row; no entities | Medium-high | ✅ Implemented |
| 3 | Plan derived from the annotations | No manual plan; projection and serialization cannot diverge | High | ✅ Implemented |

With the **four phases** the proposal is complete: the client's selection reduces the response, the number of queries (from 61 to between 0 and 3) and the columns read, without changing the contract. The projection plan is derived from the same annotations as the serialization and validated at startup. Responses are identical to before, as verified by the E2E tests and the equivalence test.

The only part still written by hand per resource is the assembler from rows to beans (section 7.5).
