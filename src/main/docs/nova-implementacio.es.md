# Nueva Implementación de Filtrado de Modelos - Análisis y Propuesta

[Català](nova-implementacio.md) · [English](nova-implementacio.en.md) · **Español**

## Tabla de Contenidos
1. [Introducción](#1-introducción)
2. [Comparación entre Implementaciones](#2-comparación-entre-implementaciones)
3. [Análisis de Diferencias de Versiones](#3-análisis-de-diferencias-de-versiones)
4. [Propuesta de Implementación Detallada](#4-propuesta-de-implementación-detallada)
5. [Plan de Migración](#5-plan-de-migración)
6. [Conclusión y Recomendación](#6-conclusión-y-recomendación)

---

## 1. Introducción

### 1.1. Contexto

El proyecto tiene una implementación de filtrado de modelos basada en:
- `@FilteredView` en los campos
- `FilterInterceptor` (HandlerInterceptor)
- `FilteredViewPropertyFilter` (SimpleBeanPropertyFilter de Jackson 2)
- `FilterContext` (ThreadLocal)
- `@JsonFilter` en los modelos
- Un `ObjectMapper` de Jackson 2 con un `FilterProvider` registrado en `FilterConfiguration`

El documento [arquitectura-models-dinamics.es.md](./arquitectura-models-dinamics.es.md) propone una arquitectura alternativa basada en:
- `@ModelBasic` y `@ModelSections` en los campos
- `ModelFilteringAdvice` (ResponseBodyAdvice)
- `ModelFilteringModule` (módulo Jackson)
- `ModelSelection` (inmutable, sin estado)

### 1.2. Objetivo

Este documento analiza las diferencias entre ambas implementaciones, evalúa la compatibilidad con Spring Boot 4.1.x (Spring Framework 7, Jackson 3) y describe la implementación de la nueva arquitectura adaptada a estas versiones.

### 1.3. Estado de la implementación actual

**La implementación actual no funciona sobre HTTP con Spring Boot 4.1.1.** Verificado levantando la aplicación:

```
GET /vehicles      → 500  Cannot resolve PropertyFilter with id 'vehicleModelFilter'; no FilterProvider configured
GET /brands        → 500  (mismo error con 'brandFilter')
GET /vehicles?m=Z  → 400  (la validación del interceptor sí funciona)
```

Causa: Spring Boot 4 serializa las respuestas MVC con **Jackson 3** (`tools.jackson`, convertidor `JacksonJsonHttpMessageConverter`). El `ObjectMapper` de `FilterConfiguration` es de **Jackson 2** (`com.fasterxml.jackson.databind`) y MVC no lo utiliza nunca. Jackson 3 mantiene el paquete de anotaciones `com.fasterxml.jackson.annotation`, de modo que sí lee `@JsonFilter`, pero su mapper no tiene ningún `FilterProvider` y la serialización falla.

Los tests no lo detectaban porque ninguno pasa por la capa de serialización HTTP: los tests de controller invocan el método Java directamente y los tests de filtro construyen un `ObjectMapper` de Jackson 2 propio.

---

## 2. Comparación entre Implementaciones

### 2.1. Comparativa General

| **Aspecto** | **Implementación Actual** | **Arquitectura Propuesta** | **Valoración** |
|-------------|--------------------------|---------------------------|---------------|
| **Mecanismo Principal** | Interceptor (preHandle) | ResponseBodyAdvice (antes de escribir) | ✅ Propuesta más natural |
| **Estado** | ThreadLocal (FilterContext) | `FilterProvider` inmutable por respuesta (hint de escritura) | ✅ Propuesta más segura |
| **Validación** | HTTP 400 por códigos inválidos | HTTP 400 por códigos inválidos y valores repetidos | ✅ Equivalente, propuesta más completa |
| **Formato Códigos** | Separados por comas (`B,P,E`), sin distinguir mayúsculas | Concatenados (`BPE`), distinguiendo mayúsculas | ⚠️ Cambio de API |
| **Anotaciones** | `@FilteredView(filters={...})` | `@ModelBasic` + `@ModelSections({...})` | ✅ Propuesta más expresiva |
| **Cascada** | Vía ThreadLocal | Vía el mismo `FilterProvider` del `ObjectWriter` | ✅ Propuesta más elegante |
| **Vary Header** | No implementado | Siempre en endpoints filtrados | ✅ Propuesta mejor para cachés |
| **Aislamiento** | `ObjectMapper` Jackson 2 global (no usado por MVC) | Copia privada del `JsonMapper` para el convertidor MVC de servidor | ✅ Propuesta más aislada |
| **Compatibilidad Boot 4** | ❌ Rota (HTTP 500) | ✅ Diseñada para Spring 7 / Jackson 3 | ✅ Propuesta |

### 2.2. Comparativa de Componentes

#### Anotaciones

| **Aspecto** | **Actual** | **Propuesta** |
|-------------|------------|---------------|
| **Propósito** | `@FilteredView` en los campos que se pueden filtrar | `@ModelBasic` (siempre incluido) + `@ModelSections` (opcional) |
| **Comportamiento de campos sin anotación** | Siempre incluidos | **Excluidos** si el bean tiene alguna marca |
| **Validación de combinaciones** | No valida | **Rechaza** `@ModelBasic` + `@ModelSections` en la misma propiedad |
| **Necesita `@JsonFilter` en el modelo** | Sí | **No**: el módulo asigna el filtro a los beans marcados |

**Ejemplo Actual:**
```java
@JsonFilter("vehicleModelFilter")
public class VehicleModelBean {
    @FilteredView(filters = {FiltersModel.Brand})
    private BrandBean brand;
    private String model;
}
```

**Ejemplo Propuesto:**
```java
public class VehicleModelBean {
    @ModelSections(ModelPart.BRAND)
    private BrandBean brand;
    @ModelBasic
    private String model;
}
```

#### Mecanismo de Filtrado

| **Aspecto** | **Actual** | **Propuesta** |
|-------------|------------|---------------|
| **Tecnología** | `SimpleBeanPropertyFilter` + `@JsonFilter` (Jackson 2) | `AnnotationIntrospector` + `PropertyFilter` (Jackson 3) |
| **Integración** | `ObjectMapper` Jackson 2 separado | Converter JSON de servidor con una copia del `JsonMapper` de Boot |
| **Impacto** | Ninguno en MVC (no se usa); rompe la serialización de beans con `@JsonFilter` | Solo los converters MVC de servidor; cliente HTTP y `JsonMapper` compartido intactos |
| **Cascada** | ThreadLocal | `FilterProvider` del `ObjectWriter`, compartido por todo el grafo |
| **Concurrencia** | ThreadLocal, problemático en asíncrono | Ningún estado mutable; un `FilterProvider` por respuesta |

#### Configuración

| **Aspecto** | **Actual** | **Propuesta** |
|-------------|------------|---------------|
| **Propiedad** | `spring.application.models.filtered` (ahora ignorada) | `models.filtratge` |
| **Condiciones** | Ninguna (ahora mismo siempre activo) | Servlet + `JsonMapper` + Spring MVC + propiedad |
| **Registro** | `@Configuration` escaneada | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

#### Contrato HTTP

| **Aspecto** | **Actual** | **Propuesta** |
|-------------|------------|---------------|
| **Parámetro** | `m` (query) | `m` (query) |
| **Header** | `x-filter-model` | `X-Model-Sections` |
| **Origen** | Parámetro y, si no está, header | Parámetro y, si no está, header (header ignorado y no validado si hay parámetro) |
| **Validación** | 400 por códigos inválidos | 400 por códigos inválidos o valor repetido |
| **Formato** | `B,P,E` | `BPE` |
| **Vary Header** | No implementado | `Vary: X-Model-Sections` en todas las respuestas de endpoints filtrados |

### 2.3. Ventajas de la Implementación Actual

✅ **Más sencilla**: menos componentes
✅ **Formato de códigos con comas**: quizá más legible para humanos

### 2.4. Desventajas de la Implementación Actual

❌ **No funciona con Spring Boot 4**: depende de Jackson 2, que MVC ya no usa (HTTP 500).

❌ **Problemas de ThreadLocal**:
   - Requiere `FilterContext.clear()` manual en `afterCompletion`
   - Problemático en procesamiento asíncrono (`DeferredResult`, `@Async`, hilos virtuales con cambio de hilo)

❌ **Menos robusta**:
   - No valida combinaciones de anotaciones
   - No distingue entre campos básicos y opcionales
   - Obliga a declarar `@JsonFilter` en cada modelo, y el modelo falla en cualquier mapper sin `FilterProvider`

❌ **Menos estándar**:
   - No utiliza `ResponseBodyAdvice`
   - No implementa `Vary`

### 2.5. Ventajas de la Arquitectura Propuesta

✅ **Más segura**: sin `ThreadLocal`; la selección es un objeto inmutable ligado a la escritura de cada respuesta.

✅ **Más aislada**: solo el convertidor JSON de servidor usa el mapper con el módulo. El `JsonMapper` de Boot y los convertidores de cliente (`RestClient`, `RestTemplate`) no cambian.

✅ **Más estándar**: `ResponseBodyAdvice` + hints de escritura son el mecanismo previsto por Spring 7 para ajustes de serialización por respuesta.

✅ **Más robusta**: validación estricta, distinción entre básico y secciones, detección de configuraciones incorrectas.

✅ **Más flexible**: query parameter y header utilizables a la vez, con nombres personalizables por endpoint.

---

## 3. Análisis de Diferencias de Versiones

### 3.1. Spring Boot 2.5.x vs 4.1.1

La arquitectura original se escribió para Spring Boot 2.5 (Spring 5, Jackson 2.12, `javax.servlet`). Spring Boot 4.1.1 cambia la base de forma significativa.

#### Componentes de Spring

| **Componente** | **Spring Boot 2.5.x** | **Spring Boot 4.1.1 (Spring 7.0.x)** | **Impacto** |
|---------------|----------------------|----------------------|-------------|
| **Servlet API** | `javax.servlet` | `jakarta.servlet` | Cambio de paquete |
| **Convertidor JSON** | `MappingJackson2HttpMessageConverter` | `JacksonJsonHttpMessageConverter` (Jackson 3). El de Jackson 2 está **deprecated** | Hay que usar el de Jackson 3 |
| **`MappingJacksonValue`** | Mecanismo habitual para vistas/filtros | **Deprecated**. El convertidor de Jackson 3 lo rechaza (`UnsupportedOperationException: MappingJacksonValue is not supported, use hints instead`) | No se puede usar |
| **`ResponseBodyAdvice`** | `beforeBodyWrite` | `beforeBodyWrite` + **`determineWriteHints`** | Las opciones de serialización se pasan como hints |
| **Hints soportados por el convertidor** | — | `JsonView` (clave `com.fasterxml.jackson.annotation.JsonView`) y `FilterProvider` (clave `tools.jackson.databind.ser.FilterProvider`) | El filtro por respuesta se pasa como hint |
| **Registro de convertidores** | `WebMvcConfigurer.configureMessageConverters(List)` | `HttpMessageConverters.ServerBuilder`; Boot aplica los beans `ServerHttpMessageConvertersCustomizer` en orden. La variante con `List` está deprecated | Personalizar vía customizer |
| **Auto-configuración** | `META-INF/spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` + `@AutoConfiguration` | Nuevo formato |

#### Componentes de Jackson

| **Componente** | **Jackson 2.12 (Boot 2.5)** | **Jackson 3.1 (Boot 4.1)** |
|---------------|----------------------|----------------------|
| **Paquete** | `com.fasterxml.jackson.databind` | `tools.jackson.databind` |
| **Anotaciones** | `com.fasterxml.jackson.annotation` | `com.fasterxml.jackson.annotation` (**sin cambios**) |
| **Mapper** | `ObjectMapper` mutable | `JsonMapper` inmutable; se crea con `JsonMapper.builder()` o `mapper.rebuild()` |
| **Contexto de serialización** | `SerializerProvider` | `SerializationContext` |
| **Serializadores** | `JsonSerializer`, `BeanSerializerModifier` | `ValueSerializer`, `ValueSerializerModifier` |
| **Módulos** | `com.fasterxml.jackson.databind.Module` | `tools.jackson.databind.JacksonModule` (`SimpleModule` sigue existiendo) |
| **`java.time` / `Optional`** | Módulos `jsr310` / `jdk8` separados | Integrados en `jackson-databind` |
| **Fechas** | `WRITE_DATES_AS_TIMESTAMPS` activado por defecto | Desactivado por defecto (ISO-8601) |

### 3.2. Decisiones de Adaptación a Spring Boot 4

1. **Selección como hint `FilterProvider`, no como atributo de `ObjectWriter`.** El convertidor de Jackson 3 aplica el `FilterProvider` del hint con `ObjectWriter.with(filters)` para esa escritura. No hace falta heredar ni decorar el convertidor, y el hint es por respuesta.
2. **`AnnotationIntrospector` en lugar de `BeanSerializerModifier`.** El módulo asigna un id de filtro (`findFilterId`) solo a los beans con `@ModelBasic`/`@ModelSections`. Los beans sin marcas no reciben ningún filtro y se serializan exactamente igual. Los modelos no necesitan `@JsonFilter`.
3. **Comportamiento sin selección.** La copia privada del mapper tiene un `FilterProvider` por defecto que deja pasar todas las propiedades. Sin hint, la salida es idéntica a la de Jackson sin módulo.
4. **Mapper privado para MVC.** Un `ServerHttpMessageConvertersCustomizer` ordenado después del de Boot sustituye el convertidor JSON de servidor por un `JacksonJsonHttpMessageConverter` construido con `jsonMapper.rebuild()` + el módulo. El bean `JsonMapper` de Boot y los convertidores de cliente no cambian.
5. **Paso de la selección entre `beforeBodyWrite` y `determineWriteHints`.** Spring llama primero a `beforeBodyWrite` (que recibe la petición y la respuesta) y después a `determineWriteHints` (que no las recibe). El advice valida la selección y añade `Vary` en `beforeBodyWrite`, y guarda la `ModelSelection` como atributo de la **petición**. `determineWriteHints` la recupera de la petición actual. Es estado ligado a la petición, no compartido; no se guarda en ningún campo singleton, `ThreadLocal` propio ni mapper.
6. **Biblioteca fuera del escaneo de componentes.** El `ModelFilteringAdvice` debe llevar `@ControllerAdvice` (Spring solo descubre advices anotados en el tipo), que es un estereotipo `@Component`. Para evitar que se registre sin la propiedad activa, el paquete de la biblioteca (`cat.albirar.dynmod.filtering`) queda fuera del escaneo de la aplicación (`cat.albirar.dynmod.demo`) y solo se carga por auto-configuración.

### 3.3. Conclusión de Compatibilidad

✅ **La arquitectura es aplicable a Spring Boot 4.1** manteniendo el contrato observable del documento original. Cambia el mecanismo interno (hint `FilterProvider` + `AnnotationIntrospector` en lugar de atributo de `ObjectWriter` + `BeanSerializerModifier`), no el comportamiento.

---

## 4. Propuesta de Implementación Detallada

### 4.1. Estructura de Paquetes

```
src/main/java/cat/albirar/dynmod/
├── filtering/                               # "Biblioteca" de filtrado (fuera del escaneo de la app)
│   ├── model/                               # Sin dependencias de Spring ni de Jackson databind
│   │   ├── ModelPart.java                   # Secciones (B, M, P, E)
│   │   ├── ModelBasic.java
│   │   ├── ModelSections.java
│   │   ├── ModelSelection.java              # Parser inmutable
│   │   └── InvalidModelSelectionException.java
│   ├── jackson/                             # Integración Jackson 3, sin Spring
│   │   ├── ModelFilteringModule.java
│   │   ├── ModelFilteringAnnotationIntrospector.java
│   │   ├── ModelSectionsPropertyFilter.java
│   │   └── ModelFilterProvider.java
│   └── web/                                 # Integración Spring MVC
│       ├── ModelFiltering.java              # Opt-in (sin dependencias de Spring)
│       ├── ModelFilteringAdvice.java
│       ├── ModelFilteringException.java     # 400
│       └── ModelFilteringAutoConfiguration.java
└── demo/                                    # Aplicación (scanBasePackages)
```

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
cat.albirar.dynmod.filtering.web.ModelFilteringAutoConfiguration
```

> Nota: en un proyecto real `ModelPart` lo definiría cada servicio (las secciones son propias del dominio). En esta demo forma parte del paquete `filtering.model` por simplicidad.

### 4.2. Componentes

#### 4.2.1. Anotaciones y enumerados

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

El código es un `char`: cada carácter del valor de la petición se convierte en una sección. La comparación distingue mayúsculas.

#### 4.2.2. `ModelSelection`

```java
public final class ModelSelection {
    private final Set<ModelPart> parts;                    // EnumSet no modificable

    public static ModelSelection parse(String value) { ... } // InvalidModelSelectionException
    public boolean includesAny(ModelPart[] candidates) { ... }
    public Set<ModelPart> parts() { ... }
}
```

- Valor vacío → selección vacía (válida).
- Códigos repetidos dentro del valor → se deduplican.
- Carácter desconocido → `InvalidModelSelectionException` (subclase de `IllegalArgumentException`, sin dependencias HTTP).

#### 4.2.3. Módulo Jackson 3

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
- Solo actúa sobre `AnnotatedClass`.
- Recorre campos y métodos del bean (incluida la jerarquía). Si ninguno tiene marcas, devuelve `null` → bean intacto.
- Si algún miembro tiene `@ModelBasic` y `@ModelSections` a la vez → `IllegalStateException` con el nombre del miembro.
- Si el bean tiene marcas y, además, un `@JsonFilter` propio → `IllegalStateException` (configuración ambigua).
- En otro caso devuelve `FILTER_ID`.

Como se inserta como introspector primario y Jackson guarda los serializadores en caché, la inspección se hace una sola vez por clase y mapper.

`ModelSectionsPropertyFilter` (extiende `SimpleBeanPropertyFilter`), inmutable:
- Sin selección → incluye todas las propiedades.
- Con selección → incluye `@ModelBasic`, `@ModelSections` vacías y `@ModelSections` con alguna sección seleccionada (OR). Omite las demás, incluidas las que no tienen marca.
- Las propiedades incluidas se delegan en el `BeanPropertyWriter` original, de modo que `NON_NULL`, `NON_ABSENT`, `@JsonIgnore`, `@JsonFormat`, vistas, etc. siguen aplicándose.

`ModelFilterProvider` (extiende `FilterProvider`), inmutable:
- Para `FILTER_ID` devuelve su `ModelSectionsPropertyFilter`.
- Para cualquier otro id delega en el `FilterProvider` que ya tuviera el `JsonMapper` de Boot (si lo hay), para no romper los `@JsonFilter` propios de la aplicación.

#### 4.2.4. `ModelFilteringAdvice`

```java
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {

    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // JacksonJsonHttpMessageConverter + @ModelFiltering resuelto (método, después clase)
    }

    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        // 1. Añadir el nombre del header a Vary (sin duplicar, respetando "Vary: *")
        // 2. Si la respuesta ya es de error (status >= 400): no hacer nada más
        // 3. Leer el query parameter; si está ausente, leer el header; ambos ausentes → cuerpo sin cambios
        //    (si hay query parameter, el header se ignora y no se valida)
        // 4. Más de un valor en el origen utilizado → ModelFilteringException (400)
        // 5. ModelSelection.parse(valor); código inválido → ModelFilteringException (400)
        // 6. Guardar la selección como atributo de la petición
    }

    public Map<String, Object> determineWriteHints(Object body, MethodParameter returnType,
            MediaType contentType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Si la petición actual tiene selección: { FilterProvider.class.getName() → new ModelFilterProvider(selección, delegado) }
    }
}
```

Resolución de la anotación con `AnnotatedElementUtils.findMergedAnnotation`, primero sobre el método (incluye interfaces y métodos sobrescritos) y, si no la hay, sobre la clase del controller (incluye superclases e interfaces). La anotación de método sustituye por completo la de clase. El resultado se guarda en caché por método.

#### 4.2.5. `ModelFilteringAutoConfiguration`

```java
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass({ DispatcherServlet.class, JsonMapper.class })
@ConditionalOnBean(JsonMapper.class)
@ConditionalOnBooleanProperty("models.filtratge")
public class ModelFilteringAutoConfiguration {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)   // después del customizer de Boot (@Order(0))
    ServerHttpMessageConvertersCustomizer modelFilteringConvertersCustomizer(JsonMapper jsonMapper) {
        // builder.withJsonConverter(new JacksonJsonHttpMessageConverter(filteringMapper(jsonMapper)))
    }

    @Bean
    ModelFilteringAdvice modelFilteringAdvice(JsonMapper jsonMapper) { ... }
}
```

La copia se hace con `jsonMapper.rebuild().addModule(new ModelFilteringModule()).filterProvider(new ModelFilterProvider(null, original)).build()`, que conserva toda la configuración de Boot (`spring.jackson.*`, módulos registrados, etc.).

### 4.3. Modificaciones en los Modelos

Regla: en un bean marcado, **toda propiedad sin marca se omite** cuando hay selección. Por eso hay que marcar explícitamente todas las propiedades básicas.

| Modelo | Marcas |
|-------|---------|
| `VehicleModelBean` | `brand` → `BRAND`; `marketData` → `MARKET`; `parts` → `PARTS`; el resto `@ModelBasic` |
| `MarketDataBean` | `currency`, `localMarketPrice`, `marketPrice`, `margin` → `ECONOMIC`; el resto `@ModelBasic` |
| `VehiclePartBean` | `marketPrice`, `margin` → `ECONOMIC`; el resto `@ModelBasic` |
| `BrandBean` | Sin marcas: se serializa siempre completo cuando se incluye |

Se eliminan todos los `@JsonFilter`.

### 4.4. Modificaciones en los Controllers

`@EnableModelFiltering` → `@ModelFiltering` en `VehicleController` y `BrandController`. `/brands` acepta códigos válidos, pero como `BrandBean` no tiene marcas, la respuesta no cambia.

### 4.5. Configuración de la Aplicación

```yaml
models:
  filtratge: true
```

`@SpringBootApplication(scanBasePackages = "cat.albirar.dynmod.demo")`, para que la biblioteca solo se cargue por auto-configuración.

Se eliminan la configuración de Jackson 2 (`FilterConfiguration`) y las dependencias explícitas `jackson-datatype-jdk8` y `jackson-datatype-jsr310`: Jackson 3 ya soporta `Optional` y `java.time`, y `@JsonFormat` sobre `Instant` funciona igual.

---

## 5. Plan de Migración

### 5.1. Fases

#### Fase 1: Biblioteca
- [x] Paquete `filtering.model`: `ModelPart`, `ModelBasic`, `ModelSections`, `ModelSelection`
- [x] Paquete `filtering.jackson`: módulo, introspector, filtro y proveedor
- [x] Paquete `filtering.web`: `ModelFiltering`, advice, excepción, auto-configuración
- [x] Registro en `AutoConfiguration.imports`

#### Fase 2: Adaptación
- [x] Marcar modelos y quitar `@JsonFilter`
- [x] `@ModelFiltering` en los controllers
- [x] `models.filtratge=true` y `scanBasePackages` limitado a la aplicación

#### Fase 3: Eliminación de la antigua implementación
- [x] `FilterInterceptor`, `FilteredViewPropertyFilter`, `FilterContext`, `EnableModelFiltering`, `FilterConfiguration`, `FiltersModel`, `FilteredView` y el `ModelFilteringException` antiguo
- [x] Tests de la implementación antigua (`FilterInterceptorTest`, `FilteredViewPropertyFilterTest`)
- [x] Dependencias Jackson 2 explícitas

#### Fase 4: Tests
- [x] `ModelSelection`: códigos válidos, desconocidos, mayúsculas/minúsculas, duplicados, vacío
- [x] Módulo Jackson: básico, una sección, OR, sección vacía, sin marca, bean sin marcas, conflicto de marcas, `@JsonFilter` combinado, cascada a listas y mapas, `NON_NULL`, `@JsonIgnore`, `Optional`/`Instant`, concurrencia
- [x] Auto-configuración: sin propiedad, propiedad falsa y verdadera
- [x] Contexto Boot real + `MockMvc`: endpoints reales, query/header, preferencia del query parameter, header ignorado si hay query parameter, nombres personalizados, ausencia, vacío, repetido, sin anotación, anotación en método/clase/interfaz/superclase, sustitución de la de clase, `ResponseEntity`, listas, arrays, mapas, `Page`, errores preservados, `Vary` acumulativo

- [x] E2E sobre servidor HTTP real (`ApiE2ETest`, etiqueta `e2e`): toda la cadena HTTP → MVC → JPA → H2 → JSON con los datos de `data.sql`; secciones y cascada, todas las secciones = modelo completo, header = query parameter, preferencia, errores 400, `Vary`, endpoints sin anotación, búsqueda y paginación, concurrencia
- [x] Pruebas de humo (`SmokeTest`, etiqueta `smoke`) y `scripts/smoke-test.sh` para entornos desplegados

#### Fase 5: Validación
- [x] Levantar la aplicación (JAR empaquetado) y pasar `scripts/smoke-test.sh`

### 5.2. Cambios de API

| **Aspecto** | **Actual** | **Nuevo** | **Impacto** |
|-------------|------------|---------|-------------|
| **Formato códigos** | `B,P,E` | `BPE` | ⚠️ **Breaking change** |
| **Mayúsculas** | Indiferente | Distingue | ⚠️ **Breaking change** (`b` ya no es válido) |
| **Header** | `x-filter-model` | `X-Model-Sections` | ⚠️ **Breaking change** |
| **Origen** | Parámetro > header | Parámetro > header | ✅ Sin cambios (el header ignorado ya no se valida) |
| **Campos sin marca** | Siempre incluidos | Omitidos con selección si el bean tiene marcas | ⚠️ Hay que marcar los campos básicos |
| **Propiedad** | `spring.application.models.filtered` | `models.filtratge` | ⚠️ Configuración |

Como la implementación actual devuelve 500 sobre HTTP, en la práctica ningún consumidor puede depender del contrato antiguo.

### 5.3. Estrategia de Despliegue

1. Desplegar en desarrollo y validar.
2. Publicar el contrato (códigos, query parameter y header por endpoint) en la documentación de la API.
3. Comunicar los cambios a los consumidores.
4. Validar en el entorno de pruebas con consumidores.
5. Desplegar en producción.

### 5.4. Rollback

- `models.filtratge=false` desactiva toda la infraestructura: los endpoints devuelven el modelo completo e ignoran `m`/`X-Model-Sections`. Las anotaciones de los modelos no tienen ningún efecto sin el módulo.
- La implementación antigua se elimina; volver a ella implica revertir el código (y seguiría sin funcionar en Boot 4).

---

## 6. Conclusión y Recomendación

### 6.1. Resumen de la Comparación

| **Criterio** | **Actual** | **Propuesta** | **Ganador** |
|-------------|------------|---------------|---------------|
| **Funciona en Boot 4** | ❌ HTTP 500 | ✅ | **Propuesta** |
| **Seguridad** | ❌ ThreadLocal | ✅ Sin estado mutable | **Propuesta** |
| **Aislamiento** | ❌ Mapper global separado | ✅ Mapper privado del converter de servidor | **Propuesta** |
| **Robustez** | ❌ Sin validación de marcas | ✅ Validación estricta | **Propuesta** |
| **Estándar** | ❌ Interceptor + ThreadLocal | ✅ ResponseBodyAdvice + hints | **Propuesta** |
| **Sencillez** | ✅ Menos componentes | ❌ Más componentes | **Actual** |

### 6.2. Recomendación Final

**✅ Implementar la nueva arquitectura adaptada a Spring Boot 4.** No es solo una mejora: la implementación actual no funciona con la versión de Spring Boot del proyecto.

### 6.3. Alternativas Consideradas

1. **Arreglar la actual configurando un `FilterProvider` en el `JsonMapper` de Jackson 3**: mantendría el `ThreadLocal`, el `@JsonFilter` obligatorio y el filtro global sobre el mapper compartido.
2. **Heredar de `JacksonJsonHttpMessageConverter` y pasar la selección como atributo de `ObjectWriter`** (literal del documento original): `customizeWriter` no recibe los hints, de modo que habría que sobrescribir `writeInternal` y duplicar lógica de Spring. Descartado en favor del hint `FilterProvider`, que Spring ya soporta.
3. **Hint `FilterProvider` + `AnnotationIntrospector`**: **Recomendado e implementado**.

---

## Apéndice A: Ejemplos

### A.1. Sin selección

**Request:** `GET /vehicles?size=1`
**Response:** modelo completo, igual que Jackson sin filtrado.

### A.2. Con selección

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
Sin `marketData` (no se ha pedido `M`) y las piezas sin precios (no se ha pedido `E`).

### A.3. Error

**Request:** `GET /vehicles?m=BX`
**Response:** HTTP 400 — `Unknown model section code 'X' in 'BX'`.

---

## Apéndice B: Glosario

| **Término** | **Definición** |
|----------|---------------|
| **ResponseBodyAdvice** | Extensión de Spring MVC que se ejecuta antes de escribir el cuerpo de la respuesta |
| **Write hints** | Mapa de opciones que Spring 7 pasa al convertidor para una escritura concreta (`determineWriteHints`) |
| **FilterProvider** | Proveedor de filtros de propiedad de Jackson, aplicable por `ObjectWriter` |
| **AnnotationIntrospector** | Componente de Jackson que interpreta anotaciones; aquí asigna el filtro a los beans marcados |
| **Vary Header** | Header HTTP que indica a las cachés qué headers de la petición afectan a la respuesta |
| **ModelBasic** | Propiedad siempre incluida en respuestas filtradas |
| **ModelSections** | Propiedad asociada a una o más secciones opcionales |
| **ModelFiltering** | Opt-in de un endpoint o controller |

---

## Apéndice C: Referencias

- [Documento de Arquitectura Original](./arquitectura-models-dinamics.es.md)
- [Spring Boot 4.1 Reference](https://docs.spring.io/spring-boot/4.1/reference/)
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Spring Framework `ResponseBodyAdvice`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseBodyAdvice.html)
