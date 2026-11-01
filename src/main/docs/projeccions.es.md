# Proyecciones: de filtrar la respuesta a reducir la carga de la base de datos

[Català](projeccions.md) · [English](projeccions.en.md) · **Español**

## Tabla de Contenidos
1. [Introducción](#1-introducción)
2. [Situación de partida](#2-situación-de-partida)
3. [Principio de diseño](#3-principio-de-diseño)
4. [Fase 0: selección disponible antes del controlador](#4-fase-0-selección-disponible-antes-del-controlador) ✅ implementada
5. [Fase 1: proyección por asociaciones](#5-fase-1-proyección-por-asociaciones) ✅ implementada
6. [Fase 2: proyección por columnas](#6-fase-2-proyección-por-columnas) ✅ implementada
7. [Fase 3: plan de proyección derivado de las anotaciones](#7-fase-3-plan-de-proyección-derivado-de-las-anotaciones) ✅ implementada
8. [Estrategia de pruebas](#8-estrategia-de-pruebas)
9. [Resumen y recomendación](#9-resumen-y-recomendación)

---

## 1. Introducción

### 1.1. Contexto

El filtrado de modelos actual ([arquitectura-models-dinamics.es.md](./arquitectura-models-dinamics.es.md)) permite que el cliente elija qué secciones del modelo quiere recibir (`?m=BP`, `X-Model-Sections: M`). La selección se aplica **al serializar** la respuesta: reduce la carga de red, pero la base de datos y el mapeo a DTO hacen exactamente el mismo trabajo con selección o sin ella.

### 1.2. Objetivo

Convertir la selección del cliente en una **proyección en la base de datos**: leer solo las asociaciones y las columnas que forman parte de las secciones pedidas, sin cambiar el contrato de la API.

---

## 2. Situación de partida

### 2.1. Flujo actual

```
Controller ─► Service ─► JPA (entidades enteras) ─► Converter (lo copia todo) ─► Jackson + filtro (descarta)
                                                                                     ▲
                                              ModelFilteringAdvice.beforeBodyWrite ──┘  (lee ?m aquí)
```

### 2.2. Medida

Sentencias SQL de `GET /vehicles?size=100` con los 25 vehículos de la demo, contadas sobre la aplicación en marcha antes de aplicar ninguna fase:

| Selección | Consultas SQL |
| --- | --- |
| Modelo completo | 61 |
| `m=` | 61 |
| `m=B` | 61 |
| `m=BMPE` | 61 |
| `m=BX` (inválida → 400) | 61 |

### 2.3. Diagnóstico

1. **La selección llegaba demasiado tarde.** Se leía en `ModelFilteringAdvice.beforeBodyWrite`, cuando el controlador ya había consultado la base de datos. Para proyectar, la selección se tiene que conocer antes de llamar al servicio. Además, una selección inválida hacía todas las consultas y después respondía 400.
2. **Problema N+1 provocado por el converter.** `brand`, `marketData` y `parts` son asociaciones lazy. `VehicleEntityToVehicleModelBeanConverter` las recorre todas y, con `spring.jpa.open-in-view` activado por defecto, cada acceso lanza una consulta: 1 (página) + marcas distintas + 25 (mercados) + 25 (piezas).
3. **La sección `E` (ECONOMIC) es a nivel de columna.** `market_price`, `margin`, `currency`... están en las mismas tablas que los datos básicos. Hibernate no carga entidades parciales, así que ahorrar estas columnas exige consultas de tuplas o DTO, no de entidades.

---

## 3. Principio de diseño

> **La proyección es una optimización. El filtro Jackson sigue siendo el garante del contrato.**

| Capa | Responsabilidad |
| --- | --- |
| Proyección (datos) | No leer lo que no se ha pedido |
| Filtro Jackson (serialización) | Decidir la forma del JSON |

El filtro de serialización es imprescindible aunque haya proyección. Por ejemplo, `float marketPrice` es un primitivo: si no se carga vale `0.0`, y sin filtro aparecería en la respuesta en lugar de quedar ausente.

Consecuencias:

- Una proyección incompleta o demasiado amplia **nunca cambia la respuesta**, solo el coste.
- Las pruebas E2E existentes validan la proyección sin cambios: las respuestas tienen que ser idénticas.
- La proyección se puede adoptar endpoint a endpoint, como el filtrado.

---

## 4. Fase 0: selección disponible antes del controlador

**Estado: implementada en la rama `feature/projeccio`.**

### 4.1. Objetivo

Que la selección esté resuelta y validada antes de ejecutar el controlador, y que la capa de datos la pueda recibir como un objeto sin `null`.

### 4.2. Cambios en `ModelSelection`

| Método | Descripción |
| --- | --- |
| `ModelSelection.full()` | Selección de una petición sin `m` ni header: el modelo completo. Sustituye el uso de `null`. |
| `isFull()` | `true` solo para la selección completa |
| `includes(ModelPart)` | La sección está seleccionada |
| `includesAll(ModelPart...)` | Todas las secciones están seleccionadas. Expresa la cascada: `includesAll(MARKET, ECONOMIC)` |

`full()` **no** es equivalente a `parse("BMPE")`: la selección completa también escribe las propiedades sin marca, y una selección con todos los códigos las omite. Por eso las dos instancias no son iguales (`equals`).

`ModelFilterProvider` y `ModelSectionsPropertyFilter` siguen aceptando `null` como sinónimo de `full()`, por compatibilidad.

### 4.3. Componentes nuevos en `cat.albirar.dynmod.filtering.web`

```
petición ─► ModelSelectionInterceptor.preHandle ──► ModelSelectionResolver ──► atributo de request
              (400 aquí si es inválida)                    ▲                       │
                                                           │                       ▼
controller(…, ModelSelection selection) ◄── ModelSelectionArgumentResolver ◄───────┤
                                                                                   ▼
                                                      ModelFilteringAdvice (filtro Jackson)
```

| Clase | Responsabilidad |
| --- | --- |
| `ModelSelectionResolver` | Encuentra la `@ModelFiltering` del endpoint (con caché) y resuelve la selección: query parameter, o header si el parámetro no está. La guarda en el atributo `SELECTION_ATTRIBUTE` para que se parsee una sola vez. |
| `ModelSelectionInterceptor` | `HandlerInterceptor` que resuelve la selección de cada endpoint anotado antes del handler. Una selección inválida responde 400 (con `Vary`) sin invocar el controlador. |
| `ModelSelectionArgumentResolver` | Inyecta la selección en cualquier parámetro de tipo `ModelSelection`. Los endpoints sin `@ModelFiltering` reciben `full()`, porque su respuesta nunca se filtra. |
| `ModelFilteringAdvice` | Ya no parsea. Reutiliza la selección resuelta (o la resuelve si el interceptor no está registrado) y sigue aplicando el filtro Jackson y `Vary`. |

La auto-configuración registra el resolver como bean y añade el interceptor y el argument resolver con un `WebMvcConfigurer`, bajo la misma propiedad `models.filtratge=true`.

### 4.4. Uso desde un controlador

```java
@GetMapping
public PagedResponse<VehicleModelBean> searchVehicles(..., ModelSelection selection, Pageable pageable) {
    Page<VehicleEntity> page = vehicleService.searchVehicles(criteria, selection, pageable);
    ...
}
```

### 4.5. Cambios de comportamiento

| Caso | Antes | Ahora |
| --- | --- | --- |
| `?m=BX` | El controlador se ejecuta (61 consultas), después 400 | 400 antes del controlador (0 consultas) |
| Selección inválida en un endpoint que responde error (p. ej. 409) | 409 sin filtrar | 400: la petición es inválida antes de llegar al handler |
| El resto de casos | — | Sin cambios: todas las pruebas existentes pasan |

### 4.6. Pruebas añadidas

- `ModelSelectionTest`: `full()`, `isFull()`, `includes`, `includesAll` y que `full()` no es igual a todos los códigos.
- `ModelFilteringWebTest`: el handler recibe la selección (query, header, vacía, completa, nombres personalizados), una selección inválida no invoca el controlador y responde con `Vary`, y un endpoint sin anotación recibe `full()`.
- `ModelFilteringAutoConfigurationTest`: el `ModelSelectionResolver` se registra.

---

## 5. Fase 1: proyección por asociaciones

**Estado: implementada en la rama `feature/projeccio`.** En `/vehicles` la ha sustituido la fase 2; se mantiene como alternativa basada en entidades y como referencia de la prueba de equivalencia. La descripción del controlador de este apartado corresponde a la fase 1.

**Objetivo:** cargar solo las asociaciones de las secciones seleccionadas (`B`, `M`, `P`) y eliminar el problema N+1.

### 5.1. Servicio consciente de la selección

`VehicleService.searchVehicles(criteria, selection, pageable)`, dentro de una transacción de solo lectura:

1. **Consulta raíz paginada** con las `Specification` existentes. Si `selection.includes(BRAND)`, se añade `VehicleSpecifications.fetchBrand()`, que hace `root.fetch("brand")`. Un `ManyToOne` no altera la paginación.
2. **Si `includes(MARKET)`:** una sola consulta para toda la página, que inicializa la colección de los vehículos ya cargados en el contexto de persistencia:
   ```java
   @Query("select v from VehicleEntity v left join fetch v.marketData where v.id in :ids")
   List<VehicleEntity> fetchMarketData(@Param("ids") Collection<Long> ids);
   ```
3. **Si `includes(PARTS)`:** lo mismo con `fetchParts`.

El resultado de `fetchMarketData` y `fetchParts` no se usa directamente. La consulta devuelve las mismas instancias que ya están en el contexto de persistencia, con la colección inicializada. Así no hay que sustituir colecciones gestionadas por Hibernate (con `orphanRemoval = true`, eso haría fallar el flush).

La firma anterior `searchVehicles(criteria, pageable)` se mantiene y equivale a la selección completa.

### 5.2. Por qué no hacer `fetch join` de las colecciones en la consulta paginada

- Con paginación, Hibernate aplica `firstResult`/`maxResults` en memoria (aviso HHH90003004): lee todas las filas y descarta.
- Hacer fetch de dos `List` a la vez (`marketData` y `parts`) provoca `MultipleBagFetchException`.

Una consulta `IN (:ids)` por colección da un número de consultas constante y mantiene la paginación en la base de datos.

### 5.3. El `count` de la paginación

La `Specification` también se aplica a la consulta `count`, y en una consulta `count` no se puede hacer fetch (la entidad no está en el `select`). `fetchBrand()` detecta este caso por el tipo de resultado (`Long`) y no añade el fetch:

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

### 5.4. Converter consciente de la selección

`VehicleEntityToVehicleModelBeanConverter` tiene un método nuevo, `convert(entity, selection)`, que solo recorre las asociaciones de las secciones seleccionadas. `convert(entity)` sigue existiendo (es el `Converter` de Spring) y equivale a la selección completa.

`VehicleController` recibe la selección como parámetro (fase 0), la pasa al servicio y al converter, y depende directamente del converter en lugar del `ConversionService`, que no admite contexto adicional.

También se ha corregido el error latente de `Currency.getInstance(null)` cuando un mercado no tiene moneda.

### 5.5. Open Session in View desactivado

```yaml
spring:
  jpa:
    open-in-view: false
```

Con OSIV desactivado, la conversión del controlador se hace fuera de la sesión. Cualquier acceso a una asociación no cargada lanza `LazyInitializationException` en lugar de una consulta silenciosa. Es la red de seguridad que garantiza que la proyección funciona.

### 5.6. Resultado medido

`GET /vehicles?size=100`, 25 vehículos en una sola página (Spring Data no hace el `count` porque la página no está llena):

| Selección | Antes | Ahora | Tamaño de la respuesta |
| --- | --- | --- | --- |
| `m=` / `m=E` | 61 | 1 | 3.234 bytes |
| `m=B` / `m=BE` | 61 | 1 | 6.156 bytes |
| `m=M` / `m=P` / `m=PE` / `m=BM` | 61 | 2 | — |
| `m=MP` / `m=BMPE` / modelo completo | 61 | 3 | 7.817 bytes |
| `m=BX` (inválida) | 61 | 0 | 400 |

Los tamaños de respuesta son idénticos a los de antes: la proyección no cambia el contrato. Una página que necesita `count` (`?size=5&page=1&m=B`) hace 2 consultas.

### 5.7. Pruebas añadidas

| Prueba | Qué verifica |
| --- | --- |
| `VehicleProjectionTest` | Con las estadísticas de Hibernate, el número de sentencias para cada selección de la tabla anterior; que una selección inválida no hace ninguna consulta; y que los filtros de búsqueda sobre marca y mercados funcionan tanto si esas secciones se seleccionan como si no. Con OSIV desactivado, un acceso lazy no previsto haría fallar la petición. |
| `VehicleServiceTest` | Las colecciones solo se cargan si la sección se ha seleccionado, con los ids de la página, y nunca para una página vacía. |
| `VehicleEntityToVehicleModelBeanConverterTest` | El converter no accede a las asociaciones de secciones no seleccionadas y acepta mercados sin moneda. |

Las pruebas E2E existentes (que incluyen "todas las secciones equivalen al modelo completo") pasan sin cambios.

### 5.8. Limitaciones conocidas

- ~~**Lombok `@Data` en las entidades.**~~ **Resuelta.** `equals`, `hashCode` y `toString` recorrían las asociaciones lazy y fallaban fuera de la sesión. Las entidades ahora usan `@Getter`/`@Setter`, `@ToString` con `@ToString.Exclude` en las asociaciones, y un `equals` basado en el identificador con `hashCode` constante (no cambia cuando se asigna el id). `VehicleProjectionTest` lo verifica con entidades separadas de la sesión.
- **Doble join a `brands`.** Cuando se filtra por marca y también se selecciona `B`, la consulta tiene un `join` para el filtro y otro para el fetch. El resultado es correcto; se podría reutilizar el mismo join como optimización.
- **Columnas.** Las entidades cargadas llevan todas las columnas, incluidas las de `ECONOMIC`. Ese es el alcance de la fase 2.

---

## 6. Fase 2: proyección por columnas

**Estado: implementada en la rama `feature/projeccio`. Es el camino que usa `/vehicles`.**

**Objetivo:** leer solo las columnas de las secciones seleccionadas, incluida `E`, sin cargar entidades.

### 6.1. Motor genérico: `cat.albirar.dynmod.filtering.jpa`

| Clase | Responsabilidad |
| --- | --- |
| `ProjectionPlan<T>` | Declara, para un recurso, el identificador, las columnas básicas, las columnas de cada sección (también a través de asociaciones `ManyToOne`, p. ej. `brand.country`) y las colecciones con sus propias columnas y subsecciones. |
| `ProjectionColumns` | Columnas básicas y por sección de un nivel. Calcula cuáles hay que leer para una selección, aplicando la cascada (`ECONOMIC` dentro de `MARKET` solo si hay `M` y `E`). |
| `ProjectionExecutor` | Genera y ejecuta las consultas de tuplas con Criteria API. Devuelve una `Page<ProjectedRow>`. |
| `ProjectedRow` | Valores leídos de una fila, por camino. `has(path)` y `hasCollection(name)` indican qué se ha leído; un camino leído puede valer `null`. |

El paquete no depende de la aplicación. Depende de JPA y de Spring Data JPA (`Specification`, `Pageable`) y no se auto-configura: la aplicación declara el bean `ProjectionExecutor`.

### 6.2. Plan de `/vehicles`

`VehicleProjection` reunía el plan y la conversión a `VehicleModelBean`. Desde la fase 3 el plan ya no se escribe a mano: se deriva de las anotaciones de los beans (apartado 7) y es idéntico a este:

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

### 6.3. Consultas generadas

1. **Consulta raíz paginada:** identificador + columnas básicas + columnas de las secciones `ManyToOne` seleccionadas, con un `left join` por asociación (reutilizado entre caminos). Las `Specification` existentes se aplican sin cambios con `spec.toPredicate(root, query, cb)`.
2. **Una consulta por colección seleccionada:** `from VehicleEntity v join v.marketData m where v.id in (:ids)`, con el identificador del propietario y las columnas del elemento. No hace falta conocer la entidad hija ni su referencia al padre.
3. **`count`**, solo cuando Spring Data lo necesita (`PageableExecutionUtils`), con la misma regla que `JpaSpecificationExecutor`: `count(distinct)` si un filtro ha marcado la consulta como `distinct`.

Ejemplo real para `?m=BM&commercialNamePattern=a&sort=model,desc&page=1&size=5`:

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

**`distinct` con tuplas.** La preocupación inicial queda resuelta: la consulta raíz solo contiene columnas de la raíz y de asociaciones `ManyToOne`, que dependen funcionalmente del identificador. Por tanto, `distinct` sobre estas columnas equivale a `distinct` sobre la entidad. Las expresiones de ordenación se añaden al `select`, porque con `DISTINCT` la base de datos lo exige.

### 6.4. De filas a beans

`VehicleProjection.toBean(row)` construye el bean a partir de los valores proyectados:

- La marca solo se construye si `row.has("brand.manufacturerWMI")`; las colecciones, solo si `row.hasCollection(...)`.
- Una colección vacía queda `null`, como hacía el converter de entidades, para que la respuesta sea idéntica.
- Las propiedades no proyectadas (p. ej. precios sin `E`) conservan el valor por defecto, y el filtro Jackson las omite. Este es el principio de diseño del apartado 3.

### 6.5. Integración

- `VehicleService.searchVehicleModels(criteria, selection, pageable)` ejecuta el plan y devuelve `Page<VehicleModelBean>`.
- `VehicleController` lo usa y ya no depende del converter.
- El camino de la fase 1 (`searchVehicles(criteria, selection, pageable)` + `convert(entity, selection)`) se mantiene como alternativa basada en entidades y como referencia de la prueba de equivalencia.

### 6.6. Resultado medido

`GET /vehicles?size=100`:

| Selección | Consultas | Tablas y columnas leídas | Tamaño de la respuesta |
| --- | --- | --- | --- |
| `m=` | 1 | `vehicles` (columnas básicas) | 3.234 bytes |
| `m=B` | 1 | `vehicles` + `brands` (join) | 6.156 bytes |
| `m=M` | 2 | + `vehicle_market_data` sin `currency`, precios ni `margin` | 3.896 bytes |
| `m=ME` | 2 | + `vehicle_market_data` con las columnas económicas | 4.379 bytes |
| `m=BMPE` / modelo completo | 3 | todas | 7.817 bytes |
| `m=BX` | 0 | — | 400 |

El número de consultas es el de la fase 1, pero ahora ninguna consulta lee columnas de secciones no seleccionadas, y no se crea ninguna entidad ni contexto de persistencia.

### 6.7. Pruebas añadidas

| Prueba | Qué verifica |
| --- | --- |
| `ProjectionColumnsTest` | Columnas por selección, cascada de secciones anidadas y caminos repetidos. |
| `ProjectedRowTest` | Diferencia entre valor `null` y camino no leído; colecciones presentes solo si se han leído. |
| `VehicleProjectionTest` | Con un `StatementInspector` de Hibernate que registra el SQL: `m=` solo lee `vehicles`; `m=B` hace el join a `brands` en la misma consulta; `m=MP` no lee `currency`, `market_price` ni `margin`; `m=ME` sí; `m=PE` no toca `vehicle_market_data`. |
| `VehicleProjectionEquivalenceTest` | 80 casos (10 selecciones × 8 búsquedas con filtros, páginas, ordenaciones y `distinct`): el JSON filtrado de la fase 2 es idéntico al de la fase 1, y los totales coinciden. |

### 6.8. Limitaciones conocidas

- **Ordenación.** Solo por caminos de la raíz o de asociaciones `ManyToOne`. Ordenar por un camino de una colección duplicaría filas.
- ~~**Validación del plan.**~~ **Resuelta** (apartado 6.9): el plan se valida contra el metamodelo JPA en el arranque.
- ~~**Plan y anotaciones duplicados.**~~ **Resuelta** por la fase 3 (apartado 7).
- **Tamaño de página.** Las colecciones se leen con `in (:ids)`. Con páginas muy grandes, convendría partir la lista.
- **Tipos.** `ProjectedRow.get` hace un cast no verificado; un error de tipo en el assembler aparece en tiempo de ejecución.

### 6.9. Validación del plan

`ProjectionExecutor.validate(plan)` comprueba el plan contra el metamodelo JPA e informa de **todos** los problemas a la vez con una `InvalidProjectionPlanException`. Reglas, las mismas de las que depende el ejecutor:

- El tipo raíz es una entidad, y el identificador existe en ella.
- Cada camino termina en un atributo básico. Los segmentos intermedios solo pueden ser asociaciones `ManyToOne` o `OneToOne`.
- Cada colección es un atributo plural de la raíz con elementos entidad o embeddable, y sus caminos cumplen las reglas anteriores desde el tipo del elemento.

Cada plan se valida una sola vez. `findAll` valida el plan antes de la primera consulta, y la demo lo adelanta al arranque al crear el bean:

```java
@Bean
ProjectionExecutor projectionExecutor(EntityManager entityManager) {
    ProjectionExecutor executor = new ProjectionExecutor(entityManager);
    // Desde la fase 3: deriva el plan de los beans y lo valida
    VehicleProjection.plan(executor);
    return executor;
}
```

Con un error tipográfico (`brand.contry`), la aplicación no arranca:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - unknown attribute 'contry' of BrandEntity in path 'brand.contry'
```

`ProjectionPlanValidatorTest` cubre el plan de la demo, un tipo que no es entidad, atributos e identificador desconocidos, caminos que terminan en una asociación, caminos que atraviesan colecciones o atributos básicos, colecciones inexistentes o que no lo son, y que el ejecutor no consulta con un plan inválido.

---

## 7. Fase 3: plan de proyección derivado de las anotaciones

**Estado: implementada en la rama `feature/projeccio`.**

**Objetivo:** no mantener el `ProjectionPlan` a mano. Las secciones de la proyección salen de las mismas anotaciones que usa la serialización, de modo que no pueden divergir.

### 7.1. Uso

Solo hay que indicar el atributo de la entidad cuando el nombre no coincide con el de la propiedad:

```java
@ModelBasic
@ModelSource("constructionYear")
private int construction;
```

El ejecutor deriva el plan, lo valida y lo guarda (una vez por par modelo–entidad):

```java
ProjectionPlan<VehicleEntity> plan = executor.planFor(VehicleModelBean.class, VehicleEntity.class);
```

`VehicleProjection.plan(executor)` encapsula esta llamada. La configuración de la demo la hace al crear el bean `ProjectionExecutor`, de modo que un modelo que no encaje con las entidades detiene el arranque. Por ejemplo, si se quita el `@ModelSource` anterior:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - property VehicleModelBean.construction: unknown attribute 'construction' of VehicleEntity (use @ModelSource if the name differs)
```

`ProjectionPlan.fromModel(metamodel, modelo, entidad)` devuelve el plan sin validar, para quien lo quiera usar fuera del ejecutor.

### 7.2. Reglas de derivación

**Sección de cada propiedad** (las mismas reglas que el filtro de serialización):

| Propiedad | Se lee |
| --- | --- |
| `@ModelBasic`, o `@ModelSections` sin secciones | Siempre que se lee el modelo que la contiene |
| `@ModelSections(X, Y)` | Si se selecciona `X` o `Y`, junto con las secciones de las propiedades que la contienen |
| Sin marca, en un modelo con marcas | Solo con la selección completa |
| Cualquier propiedad de un modelo sin marcas | Siempre que se lee el modelo |

**Tipo del atributo de origen** (`@ModelSource` o el nombre de la propiedad):

| Atributo de la entidad | Resultado |
| --- | --- |
| Básico | Una columna |
| `ManyToOne`, `OneToOne` o `Embedded` | Las columnas del modelo anidado (también dentro de un `Optional`), con el prefijo de la asociación |
| Colección | Una colección del plan. El elemento es el valor de un `Map`, el elemento de una `Collection` o de un array |

Las propiedades con `@JsonIgnore`, `static` o `transient` se ignoran. Las anotaciones se buscan en el campo y, si no están, en el getter, como hace la serialización.

### 7.3. Generalización de `ProjectionColumns`

Para poder representar cualquier modelo, `ProjectionColumns` admite ahora:

- **`sections(List.of(BRAND, ECONOMIC), ...)`**: columnas que solo se leen si todas las secciones están seleccionadas. Es el caso de una sección dentro de un modelo anidado por una asociación `ManyToOne`.
- **`fullOnly(...)`**: columnas que solo se leen con la selección completa, para las propiedades sin marca de un modelo marcado.

`ProjectionPlan.toString()` y `ProjectionColumns.toString()` describen el plan en orden canónico. Dos planes equivalentes se describen igual, lo que permite compararlos en las pruebas.

### 7.4. Pruebas añadidas

| Prueba | Qué verifica |
| --- | --- |
| `ModelPlanDeriverTest` | El plan derivado de `VehicleModelBean` es idéntico al que se había escrito a mano en la fase 2. Cubre también: `@ModelSource`, modelos anidados con y sin marcas, `Optional`, propiedades en dos secciones, propiedades sin marca, `@JsonIgnore`, `Map` y `List` como colecciones, y los errores (atributos desconocidos, orígenes que atraviesan colecciones, colecciones sin sección única, elemento desconocido, colecciones anidadas, tipo que no es entidad). También que el ejecutor deriva cada plan una sola vez y lo valida. |
| `ProjectionColumnsTest` | Secciones anidadas, columnas solo para la selección completa y descripción canónica. |

Las pruebas de equivalencia y de SQL de la fase 2 pasan sin cambios con el plan derivado.

### 7.5. Alcance y limitaciones

- **El assembler sigue siendo manual.** `VehicleProjection.toBean` convierte los valores en beans. Las diferencias de forma entre entidad y bean (`List` → `Map` con clave, `String` → `Currency`, enums duplicados, `Optional`) hacen que automatizarlo se acerque a un mini-ORM. La fase 3 automatiza lo que podía divergir de las anotaciones (qué columnas se leen para cada sección) y deja explícita la conversión.
- **Colecciones.** Solo como propiedades directas del modelo raíz, y en una sola sección, porque el ejecutor las lee con una consulta por colección desde la raíz.
- **Propiedades calculadas.** Una propiedad sin atributo en la entidad es un error de derivación. Si hace falta, hay que marcarla con `@JsonIgnore` o calcularla fuera del modelo proyectado.
- **Descubrimiento por campos.** Las propiedades se descubren por los campos del modelo y de sus superclases. Un modelo sin campos (solo getters) no se puede derivar.

---

## 8. Estrategia de pruebas

| Tipo | Qué verifica | Estado |
| --- | --- | --- |
| **Equivalencia** | Las E2E existentes pasan sin cambios y las respuestas son idénticas a las de antes de la proyección. | ✅ Fase 1 |
| **Coste** | Con `hibernate.generate_statistics=true`, el número de sentencias por selección corresponde a la tabla de la fase 1. | ✅ Fase 1 (`VehicleProjectionTest`) |
| **Guardrail** | Con `open-in-view=false`, ninguna prueba provoca `LazyInitializationException`. | ✅ Fase 1 |
| **Ausencia de columnas** | El SQL de cada selección no contiene las tablas ni las columnas de secciones no seleccionadas. | ✅ Fase 2 (`VehicleProjectionTest`) |
| **Equivalencia entre fases** | Proyección por columnas y carga de entidades producen el mismo JSON filtrado. | ✅ Fase 2 (`VehicleProjectionEquivalenceTest`) |

---

## 9. Resumen y recomendación

| Fase | Alcance | Ganancia | Complejidad | Estado |
| --- | --- | --- | --- | --- |
| 0 | Selección resuelta antes del controlador | Validación sin tocar la BD; base para el resto | Baja | ✅ Implementada |
| 1 | Asociaciones por sección, sin N+1 | 61 → 1–3 consultas | Media | ✅ Implementada |
| 2 | Columnas por sección (incluida `E`) | Menos datos leídos por fila; sin entidades | Media-alta | ✅ Implementada |
| 3 | Plan derivado de las anotaciones | Sin plan manual; proyección y serialización no pueden divergir | Alta | ✅ Implementada |

Con las **cuatro fases** la propuesta queda completa: la selección del cliente reduce la respuesta, el número de consultas (de 61 a entre 0 y 3) y las columnas leídas, sin cambiar el contrato. El plan de proyección se deriva de las mismas anotaciones que la serialización y se valida en el arranque. Las respuestas son idénticas a las de antes, lo que verifican las pruebas E2E y la de equivalencia.

La única parte que sigue escrita a mano por recurso es el assembler de filas a beans (apartado 7.5).
