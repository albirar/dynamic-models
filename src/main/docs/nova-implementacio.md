# Nova Implementació de Filtratge de Models - Anàlisi i Proposta

**Català** · [English](nova-implementacio.en.md) · [Español](nova-implementacio.es.md)

## Taula de Continguts
1. [Introducció](#1-introducció)
2. [Comparació entre Implementacions](#2-comparació-entre-implementacions)
3. [Anàlisi de Diferències de Versions](#3-anàlisi-de-diferències-de-versions)
4. [Proposta d'Implementació Detallada](#4-proposta-dimplementació-detallada)
5. [Pla de Migració](#5-pla-de-migració)
6. [Conclusió i Recomanació](#6-conclusió-i-recomanació)

---

## 1. Introducció

### 1.1. Context

El projecte té una implementació de filtratge de models basada en:
- `@FilteredView` als camps
- `FilterInterceptor` (HandlerInterceptor)
- `FilteredViewPropertyFilter` (SimpleBeanPropertyFilter de Jackson 2)
- `FilterContext` (ThreadLocal)
- `@JsonFilter` als models
- Un `ObjectMapper` de Jackson 2 amb un `FilterProvider` registrat a `FilterConfiguration`

El document [arquitectura-models-dinamics.md](./arquitectura-models-dinamics.md) proposa una arquitectura alternativa basada en:
- `@ModelBasic` i `@ModelSections` als camps
- `ModelFilteringAdvice` (ResponseBodyAdvice)
- `ModelFilteringModule` (mòdul Jackson)
- `ModelSelection` (immutable, sense estat)

### 1.2. Objectiu

Aquest document analitza les diferències entre ambdues implementacions, avalua la compatibilitat amb Spring Boot 4.1.x (Spring Framework 7, Jackson 3) i descriu la implementació de la nova arquitectura adaptada a aquestes versions.

### 1.3. Estat de la implementació actual

**La implementació actual no funciona sobre HTTP amb Spring Boot 4.1.1.** Verificat aixecant l'aplicació:

```
GET /vehicles      → 500  Cannot resolve PropertyFilter with id 'vehicleModelFilter'; no FilterProvider configured
GET /brands        → 500  (mateix error amb 'brandFilter')
GET /vehicles?m=Z  → 400  (la validació de l'interceptor sí que funciona)
```

Causa: Spring Boot 4 serialitza les respostes MVC amb **Jackson 3** (`tools.jackson`, convertidor `JacksonJsonHttpMessageConverter`). L'`ObjectMapper` de `FilterConfiguration` és de **Jackson 2** (`com.fasterxml.jackson.databind`) i MVC no l'utilitza mai. Jackson 3 manté el paquet d'anotacions `com.fasterxml.jackson.annotation`, de manera que sí que llegeix `@JsonFilter`, però el seu mapper no té cap `FilterProvider` i la serialització falla.

Els tests no ho detectaven perquè cap no passa per la capa de serialització HTTP: els tests de controller invoquen el mètode Java directament i els tests de filtre construeixen un `ObjectMapper` de Jackson 2 propi.

---

## 2. Comparació entre Implementacions

### 2.1. Comparativa General

| **Aspecte** | **Implementació Actual** | **Arquitectura Proposada** | **Valoració** |
|-------------|--------------------------|---------------------------|---------------|
| **Mecanisme Principal** | Interceptor (preHandle) | ResponseBodyAdvice (abans d'escriure) | ✅ Proposada més natural |
| **Estat** | ThreadLocal (FilterContext) | `FilterProvider` immutable per resposta (hint d'escriptura) | ✅ Proposada més segura |
| **Validació** | HTTP 400 per codis invàlids | HTTP 400 per codis invàlids i valors repetits | ✅ Equivalent, proposada més completa |
| **Format Codis** | Separats per comes (`B,P,E`), sense distingir majúscules | Concatenats (`BPE`), distingint majúscules | ⚠️ Canvi d'API |
| **Anotacions** | `@FilteredView(filters={...})` | `@ModelBasic` + `@ModelSections({...})` | ✅ Proposada més expressiva |
| **Cascada** | Via ThreadLocal | Via el mateix `FilterProvider` de l'`ObjectWriter` | ✅ Proposada més elegant |
| **Vary Header** | No implementat | Sempre a endpoints filtrats | ✅ Proposada millor per caches |
| **Aïllament** | `ObjectMapper` Jackson 2 global (no usat per MVC) | Còpia privada del `JsonMapper` per al convertidor MVC de servidor | ✅ Proposada més aïllada |
| **Compatibilitat Boot 4** | ❌ Trencada (HTTP 500) | ✅ Dissenyada per Spring 7 / Jackson 3 | ✅ Proposada |

### 2.2. Comparativa de Components

#### Anotacions

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Propòsit** | `@FilteredView` als camps que es poden filtrar | `@ModelBasic` (sempre inclòs) + `@ModelSections` (opcional) |
| **Comportament camps sense anotació** | Sempre inclosos | **Exclosos** si el bean té alguna marca |
| **Validació combinacions** | No valida | **Rebutja** `@ModelBasic` + `@ModelSections` a la mateixa propietat |
| **Necessita `@JsonFilter` al model** | Sí | **No**: el mòdul assigna el filtre als beans marcats |

**Exemple Actual:**
```java
@JsonFilter("vehicleModelFilter")
public class VehicleModelBean {
    @FilteredView(filters = {FiltersModel.Brand})
    private BrandBean brand;
    private String model;
}
```

**Exemple Proposat:**
```java
public class VehicleModelBean {
    @ModelSections(ModelPart.BRAND)
    private BrandBean brand;
    @ModelBasic
    private String model;
}
```

#### Mecanisme de Filtratge

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Tecnologia** | `SimpleBeanPropertyFilter` + `@JsonFilter` (Jackson 2) | `AnnotationIntrospector` + `PropertyFilter` (Jackson 3) |
| **Integració** | `ObjectMapper` Jackson 2 separat | Converter JSON de servidor amb una còpia del `JsonMapper` de Boot |
| **Impacte** | Cap a MVC (no s'usa); trenca la serialització de beans amb `@JsonFilter` | Només els converters MVC de servidor; client HTTP i `JsonMapper` compartit intactes |
| **Cascada** | ThreadLocal | `FilterProvider` de l'`ObjectWriter`, compartit per tot el graf |
| **Concurrència** | ThreadLocal, problemàtic en asíncron | Cap estat mutable; un `FilterProvider` per resposta |

#### Configuració

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Propietat** | `spring.application.models.filtered` (ara ignorada) | `models.filtratge` |
| **Condicions** | Cap (ara mateix sempre actiu) | Servlet + `JsonMapper` + Spring MVC + propietat |
| **Registre** | `@Configuration` escanejada | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

#### Contracte HTTP

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Paràmetre** | `m` (query) | `m` (query) |
| **Header** | `x-filter-model` | `X-Model-Sections` |
| **Origen** | Paràmetre i, si no hi és, header | Paràmetre i, si no hi és, header (header ignorat i no validat si hi ha paràmetre) |
| **Validació** | 400 per codis invàlids | 400 per codis invàlids o valor repetit |
| **Format** | `B,P,E` | `BPE` |
| **Vary Header** | No implementat | `Vary: X-Model-Sections` a totes les respostes d'endpoints filtrats |

### 2.3. Avantatges de la Implementació Actual

✅ **Més senzilla**: menys components
✅ **Format de codis amb comes**: potser més llegible per a humans

### 2.4. Desavantatges de la Implementació Actual

❌ **No funciona amb Spring Boot 4**: depèn de Jackson 2, que MVC ja no fa servir (HTTP 500).

❌ **Problemes de ThreadLocal**:
   - Requereix `FilterContext.clear()` manual a `afterCompletion`
   - Problemàtic en processament asíncron (`DeferredResult`, `@Async`, fils virtuals amb canvi de fil)

❌ **Menys robusta**:
   - No valida combinacions d'anotacions
   - No distingeix entre camps bàsics i opcionals
   - Obliga a declarar `@JsonFilter` a cada model, i el model falla en qualsevol mapper sense `FilterProvider`

❌ **Menys estàndard**:
   - No utilitza `ResponseBodyAdvice`
   - No implementa `Vary`

### 2.5. Avantatges de l'Arquitectura Proposada

✅ **Més segura**: sense `ThreadLocal`; la selecció és un objecte immutable lligat a l'escriptura de cada resposta.

✅ **Més aïllada**: només el convertidor JSON de servidor fa servir el mapper amb el mòdul. El `JsonMapper` de Boot i els convertidors de client (`RestClient`, `RestTemplate`) no canvien.

✅ **Més estàndard**: `ResponseBodyAdvice` + hints d'escriptura són el mecanisme previst per Spring 7 per a ajustos de serialització per resposta.

✅ **Més robusta**: validació estricta, distinció entre bàsic i seccions, detecció de configuracions incorrectes.

✅ **Més flexible**: query parameter i header utilitzables alhora, amb noms personalitzables per endpoint.

---

## 3. Anàlisi de Diferències de Versions

### 3.1. Spring Boot 2.5.x vs 4.1.1

L'arquitectura original es va escriure per Spring Boot 2.5 (Spring 5, Jackson 2.12, `javax.servlet`). Spring Boot 4.1.1 canvia la base de manera significativa.

#### Components de Spring

| **Component** | **Spring Boot 2.5.x** | **Spring Boot 4.1.1 (Spring 7.0.x)** | **Impacte** |
|---------------|----------------------|----------------------|-------------|
| **Servlet API** | `javax.servlet` | `jakarta.servlet` | Canvi de paquet |
| **Convertidor JSON** | `MappingJackson2HttpMessageConverter` | `JacksonJsonHttpMessageConverter` (Jackson 3). El de Jackson 2 està **deprecated** | Cal fer servir el de Jackson 3 |
| **`MappingJacksonValue`** | Mecanisme habitual per vistes/filtres | **Deprecated**. El convertidor de Jackson 3 el rebutja (`UnsupportedOperationException: MappingJacksonValue is not supported, use hints instead`) | No es pot fer servir |
| **`ResponseBodyAdvice`** | `beforeBodyWrite` | `beforeBodyWrite` + **`determineWriteHints`** | Les opcions de serialització es passen com a hints |
| **Hints suportats pel convertidor** | — | `JsonView` (clau `com.fasterxml.jackson.annotation.JsonView`) i `FilterProvider` (clau `tools.jackson.databind.ser.FilterProvider`) | El filtre per resposta es passa com a hint |
| **Registre de convertidors** | `WebMvcConfigurer.configureMessageConverters(List)` | `HttpMessageConverters.ServerBuilder`; Boot aplica beans `ServerHttpMessageConvertersCustomizer` en ordre. La variant amb `List` està deprecated | Personalitzar via customizer |
| **Auto-configuració** | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` + `@AutoConfiguration` | Nou format |

#### Components de Jackson

| **Component** | **Jackson 2.12 (Boot 2.5)** | **Jackson 3.1 (Boot 4.1)** |
|---------------|----------------------|----------------------|
| **Paquet** | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| **Anotacions** | `com.fasterxml.jackson.annotation` | `com.fasterxml.jackson.annotation` (**sense canvis**) |
| **Mapper** | `ObjectMapper` mutable | `JsonMapper` immutable; es crea amb `JsonMapper.builder()` o `mapper.rebuild()` |
| **Context de serialització** | `SerializerProvider` | `SerializationContext` |
| **Serialitzadors** | `JsonSerializer`, `BeanSerializerModifier` | `ValueSerializer`, `ValueSerializerModifier` |
| **Mòduls** | `com.fasterxml.jackson.databind.Module` | `tools.jackson.databind.JacksonModule` (`SimpleModule` continua existint) |
| **`java.time` / `Optional`** | Mòduls `jsr310` / `jdk8` separats | Integrats a `jackson-databind` |
| **Dates** | `WRITE_DATES_AS_TIMESTAMPS` activat per defecte | Desactivat per defecte (ISO-8601) |

### 3.2. Decisions d'Adaptació a Spring Boot 4

1. **Selecció com a hint `FilterProvider`, no com a atribut d'`ObjectWriter`.** El convertidor de Jackson 3 aplica el `FilterProvider` del hint amb `ObjectWriter.with(filters)` per a aquella escriptura. No cal subclassejar ni decorar el convertidor, i el hint és per resposta.
2. **`AnnotationIntrospector` en lloc de `BeanSerializerModifier`.** El mòdul assigna un id de filtre (`findFilterId`) només als beans amb `@ModelBasic`/`@ModelSections`. Els beans sense marques no reben cap filtre i es serialitzen exactament igual. Els models no necessiten `@JsonFilter`.
3. **Comportament sense selecció.** La còpia privada del mapper té un `FilterProvider` per defecte que deixa passar totes les propietats. Sense hint, la sortida és idèntica a la de Jackson sense mòdul.
4. **Mapper privat per a MVC.** Un `ServerHttpMessageConvertersCustomizer` ordenat després del de Boot substitueix el convertidor JSON de servidor per un `JacksonJsonHttpMessageConverter` construït amb `jsonMapper.rebuild()` + el mòdul. El bean `JsonMapper` de Boot i els convertidors de client no canvien.
5. **Pas de la selecció entre `beforeBodyWrite` i `determineWriteHints`.** Spring crida primer `beforeBodyWrite` (que rep la petició i la resposta) i després `determineWriteHints` (que no les rep). L'advice valida la selecció i afegeix `Vary` a `beforeBodyWrite` i desa la `ModelSelection` com a atribut de la **petició**. `determineWriteHints` la recupera de la petició actual. És estat lligat a la petició, no compartit; no es desa a cap camp singleton, `ThreadLocal` propi ni mapper.
6. **Biblioteca fora de l'escaneig de components.** El `ModelFilteringAdvice` ha de portar `@ControllerAdvice` (Spring només descobreix advices anotats al tipus), que és un estereotip `@Component`. Per evitar que es registri sense la propietat activa, el paquet de la biblioteca (`cat.albirar.dynmod.filtering`) queda fora de l'escaneig de l'aplicació (`cat.albirar.dynmod.demo`) i només es carrega per auto-configuració.

### 3.3. Conclusió de Compatibilitat

✅ **L'arquitectura és aplicable a Spring Boot 4.1** mantenint el contracte observable del document original. Canvia el mecanisme intern (hint `FilterProvider` + `AnnotationIntrospector` en lloc d'atribut d'`ObjectWriter` + `BeanSerializerModifier`), no el comportament.

---

## 4. Proposta d'Implementació Detallada

### 4.1. Estructura de Paquets

```
src/main/java/cat/albirar/dynmod/
├── filtering/                               # "Biblioteca" de filtratge (fora de l'escaneig de l'app)
│   ├── model/                               # Sense dependències de Spring ni de Jackson databind
│   │   ├── ModelPart.java                   # Seccions (B, M, P, E)
│   │   ├── ModelBasic.java
│   │   ├── ModelSections.java
│   │   ├── ModelSelection.java              # Parser immutable
│   │   └── InvalidModelSelectionException.java
│   ├── jackson/                             # Integració Jackson 3, sense Spring
│   │   ├── ModelFilteringModule.java
│   │   ├── ModelFilteringAnnotationIntrospector.java
│   │   ├── ModelSectionsPropertyFilter.java
│   │   └── ModelFilterProvider.java
│   └── web/                                 # Integració Spring MVC
│       ├── ModelFiltering.java              # Opt-in (sense dependències de Spring)
│       ├── ModelFilteringAdvice.java
│       ├── ModelFilteringException.java     # 400
│       └── ModelFilteringAutoConfiguration.java
└── demo/                                    # Aplicació (scanBasePackages)
```

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
cat.albirar.dynmod.filtering.web.ModelFilteringAutoConfiguration
```

> Nota: en un projecte real `ModelPart` el definiria cada servei (les seccions són pròpies del domini). En aquesta demo forma part del paquet `filtering.model` per simplicitat.

### 4.2. Components

#### 4.2.1. Anotacions i enumerats

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

El codi és un `char`: cada caràcter del valor de la petició es converteix en una secció. La comparació distingeix majúscules.

#### 4.2.2. `ModelSelection`

```java
public final class ModelSelection {
    private final Set<ModelPart> parts;                    // EnumSet no modificable

    public static ModelSelection parse(String value) { ... } // InvalidModelSelectionException
    public boolean includesAny(ModelPart[] candidates) { ... }
    public Set<ModelPart> parts() { ... }
}
```

- Valor buit → selecció buida (vàlida).
- Codis repetits dins el valor → es dedupliquen.
- Caràcter desconegut → `InvalidModelSelectionException` (subclasse d'`IllegalArgumentException`, sense dependències HTTP).

#### 4.2.3. Mòdul Jackson 3

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
- Només actua sobre `AnnotatedClass`.
- Recorre camps i mètodes del bean (inclosa la jerarquia). Si cap no té marques, retorna `null` → bean intacte.
- Si algun membre té `@ModelBasic` i `@ModelSections` alhora → `IllegalStateException` amb el nom del membre.
- Si el bean té marques i, a més, un `@JsonFilter` propi → `IllegalStateException` (configuració ambigua).
- Altrament retorna `FILTER_ID`.

Com que s'insereix com a introspector primari i Jackson guarda els serialitzadors en memòria cau, la inspecció es fa una sola vegada per classe i mapper.

`ModelSectionsPropertyFilter` (extén `SimpleBeanPropertyFilter`), immutable:
- Sense selecció → inclou totes les propietats.
- Amb selecció → inclou `@ModelBasic`, `@ModelSections` buides i `@ModelSections` amb alguna secció seleccionada (OR). Omet les altres, incloses les que no tenen marca.
- Les propietats incloses es deleguen al `BeanPropertyWriter` original, de manera que `NON_NULL`, `NON_ABSENT`, `@JsonIgnore`, `@JsonFormat`, vistes, etc. continuen aplicant-se.

`ModelFilterProvider` (extén `FilterProvider`), immutable:
- Per a `FILTER_ID` retorna el seu `ModelSectionsPropertyFilter`.
- Per a qualsevol altre id delega al `FilterProvider` que ja tingués el `JsonMapper` de Boot (si n'hi ha), per no trencar `@JsonFilter` propis de l'aplicació.

#### 4.2.4. `ModelFilteringAdvice`

```java
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {

    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // JacksonJsonHttpMessageConverter + @ModelFiltering resolt (mètode, després classe)
    }

    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        // 1. Afegir el nom del header a Vary (sense duplicar, respectant "Vary: *")
        // 2. Si la resposta ja és d'error (status >= 400): no fer res més
        // 3. Llegir el query parameter; si és absent, llegir el header; tots dos absents → cos sense canvis
        //    (si hi ha query parameter, el header s'ignora i no es valida)
        // 4. Més d'un valor a l'origen utilitzat → ModelFilteringException (400)
        // 5. ModelSelection.parse(valor); codi invàlid → ModelFilteringException (400)
        // 6. Desar la selecció com a atribut de la petició
    }

    public Map<String, Object> determineWriteHints(Object body, MethodParameter returnType,
            MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Si la petició actual té selecció: { FilterProvider.class.getName() → new ModelFilterProvider(selecció, delegat) }
    }
}
```

Resolució de l'anotació amb `AnnotatedElementUtils.findMergedAnnotation`, primer sobre el mètode (inclou interfícies i mètodes sobreescrits) i, si no n'hi ha, sobre la classe del controller (inclou superclasses i interfícies). L'anotació de mètode substitueix completament la de classe. El resultat es desa en memòria cau per mètode.

#### 4.2.5. `ModelFilteringAutoConfiguration`

```java
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ DispatcherServlet.class, JsonMapper.class })
@ConditionalOnBean(JsonMapper.class)
@ConditionalOnBooleanProperty("models.filtratge")
public class ModelFilteringAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)   // després del customizer de Boot (@Order(0))
    ServerHttpMessageConvertersCustomizer modelFilteringConvertersCustomizer(JsonMapper jsonMapper) {
        // builder.withJsonConverter(new JacksonJsonHttpMessageConverter(filteringMapper(jsonMapper)))
    }

    @Bean
    ModelFilteringAdvice modelFilteringAdvice(JsonMapper jsonMapper) { ... }
}
```

La còpia es fa amb `jsonMapper.rebuild().addModule(new ModelFilteringModule()).filterProvider(new ModelFilterProvider(null, original)).build()`, que conserva tota la configuració de Boot (`spring.jackson.*`, mòduls registrats, etc.).

### 4.3. Modificacions als Models

Regla: en un bean marcat, **tota propietat sense marca s'omet** quan hi ha selecció. Per això cal marcar explícitament totes les propietats bàsiques.

| Model | Marques |
|-------|---------|
| `VehicleModelBean` | `brand` → `BRAND`; `marketData` → `MARKET`; `parts` → `PARTS`; la resta `@ModelBasic` |
| `MarketDataBean` | `currency`, `localMarketPrice`, `marketPrice`, `margin` → `ECONOMIC`; la resta `@ModelBasic` |
| `VehiclePartBean` | `marketPrice`, `margin` → `ECONOMIC`; la resta `@ModelBasic` |
| `BrandBean` | Sense marques: es serialitza sempre complet quan s'inclou |

S'eliminen tots els `@JsonFilter`.

### 4.4. Modificacions als Controllers

`@EnableModelFiltering` → `@ModelFiltering` a `VehicleController` i `BrandController`. `/brands` accepta codis vàlids, però com que `BrandBean` no té marques, la resposta no canvia.

### 4.5. Configuració de l'Aplicació

```yaml
models:
  filtratge: true
```

`@SpringBootApplication(scanBasePackages = "cat.albirar.dynmod.demo")`, perquè la biblioteca només es carregui per auto-configuració.

S'eliminen la configuració de Jackson 2 (`FilterConfiguration`) i les dependències explícites `jackson-datatype-jdk8` i `jackson-datatype-jsr310`: Jackson 3 ja suporta `Optional` i `java.time`, i `@JsonFormat` sobre `Instant` funciona igual.

---

## 5. Pla de Migració

### 5.1. Fases

#### Fase 1: Biblioteca
- [x] Paquet `filtering.model`: `ModelPart`, `ModelBasic`, `ModelSections`, `ModelSelection`
- [x] Paquet `filtering.jackson`: mòdul, introspector, filtre i proveïdor
- [x] Paquet `filtering.web`: `ModelFiltering`, advice, excepció, auto-configuració
- [x] Registre a `AutoConfiguration.imports`

#### Fase 2: Adaptació
- [x] Marcar models i treure `@JsonFilter`
- [x] `@ModelFiltering` als controllers
- [x] `models.filtratge=true` i `scanBasePackages` limitat a l'aplicació

#### Fase 3: Eliminació de l'antiga implementació
- [x] `FilterInterceptor`, `FilteredViewPropertyFilter`, `FilterContext`, `EnableModelFiltering`, `FilterConfiguration`, `FiltersModel`, `FilteredView` i el `ModelFilteringException` antic
- [x] Tests de la implementació antiga (`FilterInterceptorTest`, `FilteredViewPropertyFilterTest`)
- [x] Dependències Jackson 2 explícites

#### Fase 4: Tests
- [x] `ModelSelection`: codis vàlids, desconeguts, majúscules/minúscules, duplicats, buit
- [x] Mòdul Jackson: bàsic, una secció, OR, secció buida, sense marca, bean sense marques, conflicte de marques, `@JsonFilter` combinat, cascada a llistes i mapes, `NON_NULL`, `@JsonIgnore`, `Optional`/`Instant`, concurrència
- [x] Auto-configuració: sense propietat, propietat falsa i certa
- [x] Context Boot real + `MockMvc`: endpoints reals, query/header, preferència del query parameter, header ignorat si hi ha query parameter, noms personalitzats, absència, buit, repetit, sense anotació, anotació a mètode/classe/interfície/superclasse, substitució de la de classe, `ResponseEntity`, llistes, arrays, mapes, `Page`, errors preservats, `Vary` acumulatiu

- [x] E2E sobre servidor HTTP real (`ApiE2ETest`, etiqueta `e2e`): tota la cadena HTTP → MVC → JPA → H2 → JSON amb les dades de `data.sql`; seccions i cascada, totes les seccions = model complet, header = query parameter, preferència, errors 400, `Vary`, endpoints sense anotació, cerca i paginació, concurrència
- [x] Proves de fum (`SmokeTest`, etiqueta `smoke`) i `scripts/smoke-test.sh` per a entorns desplegats

#### Fase 5: Validació
- [x] Aixecar l'aplicació (JAR empaquetat) i passar `scripts/smoke-test.sh`

### 5.2. Canvis d'API

| **Aspecte** | **Actual** | **Nou** | **Impacte** |
|-------------|------------|---------|-------------|
| **Format codis** | `B,P,E` | `BPE` | ⚠️ **Breaking change** |
| **Majúscules** | Indiferent | Distingeix | ⚠️ **Breaking change** (`b` ja no és vàlid) |
| **Header** | `x-filter-model` | `X-Model-Sections` | ⚠️ **Breaking change** |
| **Origen** | Paràmetre > header | Paràmetre > header | ✅ Sense canvis (el header ignorat ja no es valida) |
| **Camps sense marca** | Sempre inclosos | Omesos amb selecció si el bean té marques | ⚠️ Cal marcar els camps bàsics |
| **Propietat** | `spring.application.models.filtered` | `models.filtratge` | ⚠️ Configuració |

Com que la implementació actual retorna 500 sobre HTTP, a la pràctica cap consumidor no pot dependre del contracte antic.

### 5.3. Estratègia de Desplegament

1. Desplegar en desenvolupament i validar.
2. Publicar el contracte (codis, query parameter i header per endpoint) a la documentació de l'API.
3. Comunicar els canvis als consumidors.
4. Validar en l'entorn de proves amb consumidors.
5. Desplegar en producció.

### 5.4. Rollback

- `models.filtratge=false` desactiva tota la infraestructura: els endpoints retornen el model complet i ignoren `m`/`X-Model-Sections`. Les anotacions dels models no tenen cap efecte sense el mòdul.
- La implementació antiga s'elimina; tornar-hi implica revertir el codi (i continuaria sense funcionar a Boot 4).

---

## 6. Conclusió i Recomanació

### 6.1. Resum de la Comparació

| **Criteri** | **Actual** | **Proposada** | **Guanyador** |
|-------------|------------|---------------|---------------|
| **Funciona a Boot 4** | ❌ HTTP 500 | ✅ | **Proposada** |
| **Seguretat** | ❌ ThreadLocal | ✅ Sense estat mutable | **Proposada** |
| **Aïllament** | ❌ Mapper global separat | ✅ Mapper privat del converter de servidor | **Proposada** |
| **Robustesa** | ❌ Sense validació de marques | ✅ Validació estricta | **Proposada** |
| **Estàndard** | ❌ Interceptor + ThreadLocal | ✅ ResponseBodyAdvice + hints | **Proposada** |
| **Senzillesa** | ✅ Menys components | ❌ Més components | **Actual** |

### 6.2. Recomanació Final

**✅ Implementar la nova arquitectura adaptada a Spring Boot 4.** No és només una millora: la implementació actual no funciona amb la versió de Spring Boot del projecte.

### 6.3. Alternatives Considerades

1. **Arreglar l'actual configurant un `FilterProvider` al `JsonMapper` de Jackson 3**: mantindria el `ThreadLocal`, el `@JsonFilter` obligatori i el filtre global sobre el mapper compartit.
2. **Subclassejar `JacksonJsonHttpMessageConverter` i passar la selecció com a atribut d'`ObjectWriter`** (literal del document original): `customizeWriter` no rep els hints, de manera que caldria sobreescriure `writeInternal` i duplicar lògica de Spring. Descartat en favor del hint `FilterProvider`, que Spring ja suporta.
3. **Hint `FilterProvider` + `AnnotationIntrospector`**: **Recomanat i implementat**.

---

## Apèndix A: Exemples

### A.1. Sense selecció

**Request:** `GET /vehicles?size=1`
**Response:** model complet, igual que Jackson sense filtratge.

### A.2. Amb selecció

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
Sense `marketData` (no s'ha demanat `M`) i les peces sense preus (no s'ha demanat `E`).

### A.3. Error

**Request:** `GET /vehicles?m=BX`
**Response:** HTTP 400 — `Unknown model section code 'X' in 'BX'`.

---

## Apèndix B: Glossari

| **Terme** | **Definició** |
|----------|---------------|
| **ResponseBodyAdvice** | Extensió de Spring MVC que s'executa abans d'escriure el cos de la resposta |
| **Write hints** | Mapa d'opcions que Spring 7 passa al convertidor per a una escriptura concreta (`determineWriteHints`) |
| **FilterProvider** | Proveïdor de filtres de propietat de Jackson, aplicable per `ObjectWriter` |
| **AnnotationIntrospector** | Component de Jackson que interpreta anotacions; aquí assigna el filtre als beans marcats |
| **Vary Header** | Header HTTP que indica als caches quins headers de la petició afecten la resposta |
| **ModelBasic** | Propietat sempre inclosa en respostes filtrades |
| **ModelSections** | Propietat associada a una o més seccions opcionals |
| **ModelFiltering** | Opt-in d'un endpoint o controller |

---

## Apèndix C: Referències

- [Document d'Arquitectura Original](./arquitectura-models-dinamics.md)
- [Spring Boot 4.1 Reference](https://docs.spring.io/spring-boot/4.1/reference/)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Spring Framework `ResponseBodyAdvice`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html)
