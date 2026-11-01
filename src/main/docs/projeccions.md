# Projeccions: de filtrar la resposta a reduir la càrrega de la base de dades

**Català** · [English](projeccions.en.md) · [Español](projeccions.es.md)

## Taula de Continguts
1. [Introducció](#1-introducció)
2. [Situació de partida](#2-situació-de-partida)
3. [Principi de disseny](#3-principi-de-disseny)
4. [Fase 0: selecció disponible abans del controlador](#4-fase-0-selecció-disponible-abans-del-controlador) ✅ implementada
5. [Fase 1: projecció per associacions](#5-fase-1-projecció-per-associacions) ✅ implementada
6. [Fase 2: projecció per columnes](#6-fase-2-projecció-per-columnes) ✅ implementada
7. [Fase 3: pla de projecció derivat de les anotacions](#7-fase-3-pla-de-projecció-derivat-de-les-anotacions) ✅ implementada
8. [Estratègia de proves](#8-estratègia-de-proves)
9. [Resum i recomanació](#9-resum-i-recomanació)

---

## 1. Introducció

### 1.1. Context

El filtratge de models actual ([arquitectura-models-dinamics.md](./arquitectura-models-dinamics.md)) permet que el client triï quines seccions del model vol rebre (`?m=BP`, `X-Model-Sections: M`). La selecció s'aplica **en serialitzar** la resposta: redueix la càrrega de xarxa, però la base de dades i el mapatge a DTO fan exactament la mateixa feina amb selecció o sense.

### 1.2. Objectiu

Convertir la selecció del client en una **projecció a la base de dades**: llegir només les associacions i les columnes que formen part de les seccions demanades, sense canviar el contracte de l'API.

---

## 2. Situació de partida

### 2.1. Flux actual

```
Controller ─► Service ─► JPA (entitats senceres) ─► Converter (ho copia tot) ─► Jackson + filtre (descarta)
                                                                                     ▲
                                              ModelFilteringAdvice.beforeBodyWrite ──┘  (llegeix ?m aquí)
```

### 2.2. Mesura

Sentències SQL de `GET /vehicles?size=100` amb les 25 dades de vehicles de la demo, comptades sobre l'aplicació en marxa abans d'aplicar cap fase:

| Selecció | Consultes SQL |
| --- | --- |
| Model complet | 61 |
| `m=` | 61 |
| `m=B` | 61 |
| `m=BMPE` | 61 |
| `m=BX` (invàlida → 400) | 61 |

### 2.3. Diagnòstic

1. **La selecció arribava massa tard.** Es llegia a `ModelFilteringAdvice.beforeBodyWrite`, quan el controlador ja havia consultat la base de dades. Per projectar, la selecció s'ha de conèixer abans de cridar el servei. A més, una selecció invàlida feia totes les consultes i després responia 400.
2. **Problema N+1 provocat pel converter.** `brand`, `marketData` i `parts` són associacions lazy. `VehicleEntityToVehicleModelBeanConverter` les recorre totes i, amb `spring.jpa.open-in-view` activat per defecte, cada accés llança una consulta: 1 (pàgina) + marques diferents + 25 (mercats) + 25 (peces).
3. **La secció `E` (ECONOMIC) és a nivell de columna.** `market_price`, `margin`, `currency`... són a les mateixes taules que les dades bàsiques. Hibernate no carrega entitats parcials, de manera que estalviar aquestes columnes exigeix consultes de tuples o DTO, no d'entitats.

---

## 3. Principi de disseny

> **La projecció és una optimització. El filtre Jackson continua essent el garant del contracte.**

| Capa | Responsabilitat |
| --- | --- |
| Projecció (dades) | No llegir el que no s'ha demanat |
| Filtre Jackson (serialització) | Decidir la forma del JSON |

El filtre de serialització és imprescindible encara que hi hagi projecció. Per exemple, `float marketPrice` és un primitiu: si no es carrega val `0.0`, i sense filtre apareixeria a la resposta en lloc de quedar absent.

Conseqüències:

- Una projecció incompleta o massa àmplia **no canvia mai la resposta**, només el cost.
- Les proves E2E existents validen la projecció sense canvis: les respostes han de ser idèntiques.
- La projecció es pot adoptar endpoint a endpoint, com el filtratge.

---

## 4. Fase 0: selecció disponible abans del controlador

**Estat: implementada a la branca `feature/projeccio`.**

### 4.1. Objectiu

Que la selecció estigui resolta i validada abans d'executar el controlador, i que la capa de dades la pugui rebre com un objecte sense `null`.

### 4.2. Canvis a `ModelSelection`

| Mètode | Descripció |
| --- | --- |
| `ModelSelection.full()` | Selecció d'una petició sense `m` ni header: el model complet. Substitueix l'ús de `null`. |
| `isFull()` | `true` només per a la selecció completa |
| `includes(ModelPart)` | La secció està seleccionada |
| `includesAll(ModelPart...)` | Totes les seccions estan seleccionades. Expressa la cascada: `includesAll(MARKET, ECONOMIC)` |

`full()` **no** és equivalent a `parse("BMPE")`: la selecció completa també escriu les propietats sense marca, i una selecció amb tots els codis les omet. Per això les dues instàncies no són iguals (`equals`).

`ModelFilterProvider` i `ModelSectionsPropertyFilter` continuen acceptant `null` com a sinònim de `full()`, per compatibilitat.

### 4.3. Components nous a `cat.albirar.dynmod.filtering.web`

```
petició ─► ModelSelectionInterceptor.preHandle ──► ModelSelectionResolver ──► atribut de request
              (400 aquí si és invàlida)                    ▲                       │
                                                           │                       ▼
controller(…, ModelSelection selection) ◄── ModelSelectionArgumentResolver ◄───────┤
                                                                                   ▼
                                                      ModelFilteringAdvice (filtre Jackson)
```

| Classe | Responsabilitat |
| --- | --- |
| `ModelSelectionResolver` | Troba la `@ModelFiltering` de l'endpoint (amb memòria cau) i resol la selecció: query parameter, o header si el paràmetre no hi és. La desa a l'atribut `SELECTION_ATTRIBUTE` perquè es parsegi una sola vegada. |
| `ModelSelectionInterceptor` | `HandlerInterceptor` que resol la selecció de cada endpoint anotat abans del handler. Una selecció invàlida respon 400 (amb `Vary`) sense invocar el controlador. |
| `ModelSelectionArgumentResolver` | Injecta la selecció a qualsevol paràmetre de tipus `ModelSelection`. Els endpoints sense `@ModelFiltering` reben `full()`, perquè la seva resposta mai es filtra. |
| `ModelFilteringAdvice` | Ja no parseja. Reutilitza la selecció resolta (o la resol si l'interceptor no està registrat) i continua aplicant el filtre Jackson i `Vary`. |

L'auto-configuració registra el resolver com a bean i afegeix l'interceptor i l'argument resolver amb un `WebMvcConfigurer`, sota la mateixa propietat `models.filtratge=true`.

### 4.4. Ús des d'un controlador

```java
@GetMapping
public PagedResponse<VehicleModelBean> searchVehicles(..., ModelSelection selection, Pageable pageable) {
    Page<VehicleEntity> page = vehicleService.searchVehicles(criteria, selection, pageable);
    ...
}
```

### 4.5. Canvis de comportament

| Cas | Abans | Ara |
| --- | --- | --- |
| `?m=BX` | El controlador s'executa (61 consultes), després 400 | 400 abans del controlador (0 consultes) |
| Selecció invàlida en un endpoint que respon error (p. ex. 409) | 409 sense filtrar | 400: la petició és invàlida abans d'arribar al handler |
| La resta de casos | — | Sense canvis: totes les proves existents passen |

### 4.6. Proves afegides

- `ModelSelectionTest`: `full()`, `isFull()`, `includes`, `includesAll` i que `full()` no és igual a tots els codis.
- `ModelFilteringWebTest`: el handler rep la selecció (query, header, buida, completa, noms personalitzats), una selecció invàlida no invoca el controlador i respon amb `Vary`, i un endpoint sense anotació rep `full()`.
- `ModelFilteringAutoConfigurationTest`: el `ModelSelectionResolver` es registra.

---

## 5. Fase 1: projecció per associacions

**Estat: implementada a la branca `feature/projeccio`.** A `/vehicles` l'ha substituït la fase 2; es manté com a alternativa basada en entitats i com a referència de la prova d'equivalència. La descripció del controlador d'aquest apartat correspon a la fase 1.

**Objectiu:** carregar només les associacions de les seccions seleccionades (`B`, `M`, `P`) i eliminar el problema N+1.

### 5.1. Servei conscient de la selecció

`VehicleService.searchVehicles(criteria, selection, pageable)`, dins d'una transacció de només lectura:

1. **Consulta arrel paginada** amb les `Specification` existents. Si `selection.includes(BRAND)`, s'hi afegeix `VehicleSpecifications.fetchBrand()`, que fa `root.fetch("brand")`. Un `ManyToOne` no altera la paginació.
2. **Si `includes(MARKET)`:** una sola consulta per a tota la pàgina, que inicialitza la col·lecció dels vehicles ja carregats al context de persistència:
   ```java
   @Query("select v from VehicleEntity v left join fetch v.marketData where v.id in :ids")
   List<VehicleEntity> fetchMarketData(@Param("ids") Collection<Long> ids);
   ```
3. **Si `includes(PARTS)`:** el mateix amb `fetchParts`.

El resultat de `fetchMarketData` i `fetchParts` no es fa servir directament. La consulta torna les mateixes instàncies que ja hi ha al context de persistència, amb la col·lecció inicialitzada. Així no cal substituir col·leccions gestionades per Hibernate (amb `orphanRemoval = true`, això faria fallar el flush).

La signatura anterior `searchVehicles(criteria, pageable)` es manté i equival a la selecció completa.

### 5.2. Per què no fer `fetch join` de les col·leccions a la consulta paginada

- Amb paginació, Hibernate aplica `firstResult`/`maxResults` en memòria (avís HHH90003004): llegeix totes les files i descarta.
- Fer fetch de dues `List` alhora (`marketData` i `parts`) provoca `MultipleBagFetchException`.

Una consulta `IN (:ids)` per col·lecció dona un nombre de consultes constant i manté la paginació a la base de dades.

### 5.3. El `count` de la paginació

La `Specification` s'aplica també a la consulta `count`, i en una consulta `count` no es pot fer fetch (l'entitat no és al `select`). `fetchBrand()` detecta aquest cas pel tipus de resultat (`Long`) i no hi afegeix el fetch:

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

### 5.4. Converter conscient de la selecció

`VehicleEntityToVehicleModelBeanConverter` té un mètode nou, `convert(entity, selection)`, que només recorre les associacions de les seccions seleccionades. `convert(entity)` continua existint (és el `Converter` de Spring) i equival a la selecció completa.

`VehicleController` rep la selecció com a paràmetre (fase 0), la passa al servei i al converter, i depèn directament del converter en lloc del `ConversionService`, que no admet context addicional.

També s'ha corregit l'error latent de `Currency.getInstance(null)` quan un mercat no té moneda.

### 5.5. Open Session in View desactivat

```yaml
spring:
  jpa:
    open-in-view: false
```

Amb OSIV desactivat, la conversió del controlador es fa fora de la sessió. Qualsevol accés a una associació no carregada llança `LazyInitializationException` en lloc d'una consulta silenciosa. És la xarxa de seguretat que garanteix que la projecció funciona.

### 5.6. Resultat mesurat

`GET /vehicles?size=100`, 25 vehicles en una sola pàgina (Spring Data no fa el `count` perquè la pàgina no és plena):

| Selecció | Abans | Ara | Mida de la resposta |
| --- | --- | --- | --- |
| `m=` / `m=E` | 61 | 1 | 3.234 bytes |
| `m=B` / `m=BE` | 61 | 1 | 6.156 bytes |
| `m=M` / `m=P` / `m=PE` / `m=BM` | 61 | 2 | — |
| `m=MP` / `m=BMPE` / model complet | 61 | 3 | 7.817 bytes |
| `m=BX` (invàlida) | 61 | 0 | 400 |

Les mides de resposta són idèntiques a les d'abans: la projecció no canvia el contracte. Una pàgina que necessita `count` (`?size=5&page=1&m=B`) fa 2 consultes.

### 5.7. Proves afegides

| Prova | Què verifica |
| --- | --- |
| `VehicleProjectionTest` | Amb les estadístiques de Hibernate, el nombre de sentències per a cada selecció de la taula anterior; que una selecció invàlida no fa cap consulta; i que els filtres de cerca sobre marca i mercats funcionen tant si aquestes seccions se seleccionen com si no. Amb OSIV desactivat, un accés lazy no previst faria fallar la petició. |
| `VehicleServiceTest` | Les col·leccions només es carreguen si la secció s'ha seleccionat, amb els ids de la pàgina, i mai per a una pàgina buida. |
| `VehicleEntityToVehicleModelBeanConverterTest` | El converter no accedeix a les associacions de seccions no seleccionades i accepta mercats sense moneda. |

Les proves E2E existents (que inclouen "totes les seccions equivalen al model complet") passen sense canvis.

### 5.8. Limitacions conegudes

- ~~**Lombok `@Data` a les entitats.**~~ **Resolta.** `equals`, `hashCode` i `toString` recorrien les associacions lazy i fallaven fora de la sessió. Les entitats ara fan servir `@Getter`/`@Setter`, `@ToString` amb `@ToString.Exclude` a les associacions, i un `equals` basat en l'identificador amb `hashCode` constant (no canvia quan s'assigna l'id). `VehicleProjectionTest` ho verifica amb entitats separades de la sessió.
- **Doble join a `brands`.** Quan es filtra per marca i també se selecciona `B`, la consulta té un `join` per al filtre i un altre per al fetch. El resultat és correcte; es podria reutilitzar el mateix join com a optimització.
- **Columnes.** Les entitats carregades porten totes les columnes, incloses les de `ECONOMIC`. Això és l'abast de la fase 2.

---

## 6. Fase 2: projecció per columnes

**Estat: implementada a la branca `feature/projeccio`. És el camí que fa servir `/vehicles`.**

**Objectiu:** llegir només les columnes de les seccions seleccionades, inclosa `E`, sense carregar entitats.

### 6.1. Motor genèric: `cat.albirar.dynmod.filtering.jpa`

| Classe | Responsabilitat |
| --- | --- |
| `ProjectionPlan<T>` | Declara, per a un recurs, l'identificador, les columnes bàsiques, les columnes de cada secció (també a través d'associacions `ManyToOne`, p. ex. `brand.country`) i les col·leccions amb les seves pròpies columnes i subseccions. |
| `ProjectionColumns` | Columnes bàsiques i per secció d'un nivell. Calcula quines s'han de llegir per a una selecció, aplicant la cascada (`ECONOMIC` dins de `MARKET` només si hi ha `M` i `E`). |
| `ProjectionExecutor` | Genera i executa les consultes de tuples amb Criteria API. Retorna una `Page<ProjectedRow>`. |
| `ProjectedRow` | Valors llegits d'una fila, per camí. `has(path)` i `hasCollection(name)` diuen què s'ha llegit; un camí llegit pot valer `null`. |

El paquet no depèn de l'aplicació. Depèn de JPA i de Spring Data JPA (`Specification`, `Pageable`) i no s'auto-configura: l'aplicació declara el bean `ProjectionExecutor`.

### 6.2. Pla de `/vehicles`

`VehicleProjection` reunia el pla i la conversió a `VehicleModelBean`. Des de la fase 3 el pla ja no s'escriu a mà: es deriva de les anotacions dels beans (apartat 7) i és idèntic a aquest:

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

### 6.3. Consultes generades

1. **Consulta arrel paginada:** identificador + columnes bàsiques + columnes de les seccions `ManyToOne` seleccionades, amb un `left join` per associació (reutilitzat entre camins). Les `Specification` existents s'apliquen sense canvis amb `spec.toPredicate(root, query, cb)`.
2. **Una consulta per col·lecció seleccionada:** `from VehicleEntity v join v.marketData m where v.id in (:ids)`, amb l'identificador del propietari i les columnes de l'element. No cal conèixer l'entitat filla ni la seva referència al pare.
3. **`count`**, només quan Spring Data el necessita (`PageableExecutionUtils`), amb la mateixa regla que `JpaSpecificationExecutor`: `count(distinct)` si un filtre ha marcat la consulta com a `distinct`.

Exemple real per a `?m=BM&commercialNamePattern=a&sort=model,desc&page=1&size=5`:

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

**`distinct` amb tuples.** La preocupació inicial queda resolta: la consulta arrel només conté columnes de l'arrel i d'associacions `ManyToOne`, que depenen funcionalment de l'identificador. Per tant, `distinct` sobre aquestes columnes equival a `distinct` sobre l'entitat. Les expressions d'ordenació s'afegeixen al `select`, perquè amb `DISTINCT` la base de dades ho exigeix.

### 6.4. De files a beans

`VehicleProjection.toBean(row)` construeix el bean a partir dels valors projectats:

- La marca només es construeix si `row.has("brand.manufacturerWMI")`; les col·leccions, només si `row.hasCollection(...)`.
- Una col·lecció buida queda `null`, com feia el converter d'entitats, perquè la resposta sigui idèntica.
- Les propietats no projectades (p. ex. preus sense `E`) conserven el valor per defecte, i el filtre Jackson les omet. Aquest és el principi de disseny de l'apartat 3.

### 6.5. Integració

- `VehicleService.searchVehicleModels(criteria, selection, pageable)` executa el pla i retorna `Page<VehicleModelBean>`.
- `VehicleController` l'utilitza i ja no depèn del converter.
- El camí de la fase 1 (`searchVehicles(criteria, selection, pageable)` + `convert(entity, selection)`) es manté com a alternativa basada en entitats i com a referència de la prova d'equivalència.

### 6.6. Resultat mesurat

`GET /vehicles?size=100`:

| Selecció | Consultes | Taules i columnes llegides | Mida de la resposta |
| --- | --- | --- | --- |
| `m=` | 1 | `vehicles` (columnes bàsiques) | 3.234 bytes |
| `m=B` | 1 | `vehicles` + `brands` (join) | 6.156 bytes |
| `m=M` | 2 | + `vehicle_market_data` sense `currency`, preus ni `margin` | 3.896 bytes |
| `m=ME` | 2 | + `vehicle_market_data` amb les columnes econòmiques | 4.379 bytes |
| `m=BMPE` / model complet | 3 | totes | 7.817 bytes |
| `m=BX` | 0 | — | 400 |

El nombre de consultes és el de la fase 1, però ara cap consulta llegeix columnes de seccions no seleccionades, i no es crea cap entitat ni context de persistència.

### 6.7. Proves afegides

| Prova | Què verifica |
| --- | --- |
| `ProjectionColumnsTest` | Columnes per selecció, cascada de seccions niades i camins repetits. |
| `ProjectedRowTest` | Diferència entre valor `null` i camí no llegit; col·leccions presents només si s'han llegit. |
| `VehicleProjectionTest` | Amb un `StatementInspector` de Hibernate que registra el SQL: `m=` només llegeix `vehicles`; `m=B` fa el join a `brands` dins la mateixa consulta; `m=MP` no llegeix `currency`, `market_price` ni `margin`; `m=ME` sí; `m=PE` no toca `vehicle_market_data`. |
| `VehicleProjectionEquivalenceTest` | 80 casos (10 seleccions × 8 cerques amb filtres, pàgines, ordenacions i `distinct`): el JSON filtrat de la fase 2 és idèntic al de la fase 1, i els totals coincideixen. |

### 6.8. Limitacions conegudes

- **Ordenació.** Només per camins de l'arrel o d'associacions `ManyToOne`. Ordenar per un camí d'una col·lecció duplicaria files.
- ~~**Validació del pla.**~~ **Resolta** (apartat 6.9): el pla es valida contra el metamodel JPA a l'arrencada.
- ~~**Pla i anotacions duplicats.**~~ **Resolta** per la fase 3 (apartat 7).
- **Mida de pàgina.** Les col·leccions es llegeixen amb `in (:ids)`. Amb pàgines molt grans, convindria partir la llista.
- **Tipus.** `ProjectedRow.get` fa un cast no verificat; un error de tipus a l'assembler apareix en temps d'execució.

### 6.9. Validació del pla

`ProjectionExecutor.validate(plan)` comprova el pla contra el metamodel JPA i informa de **tots** els problemes alhora amb una `InvalidProjectionPlanException`. Regles, les mateixes de les quals depèn l'executor:

- El tipus arrel és una entitat, i l'identificador hi existeix.
- Cada camí acaba en un atribut bàsic. Els segments intermedis només poden ser associacions `ManyToOne` o `OneToOne`.
- Cada col·lecció és un atribut plural de l'arrel amb elements entitat o embeddable, i els seus camins compleixen les regles anteriors des del tipus de l'element.

Cada pla es valida una sola vegada. `findAll` valida el pla abans de la primera consulta, i la demo ho avança a l'arrencada en crear el bean:

```java
@Bean
ProjectionExecutor projectionExecutor(EntityManager entityManager) {
    ProjectionExecutor executor = new ProjectionExecutor(entityManager);
    // Des de la fase 3: deriva el pla dels beans i el valida
    VehicleProjection.plan(executor);
    return executor;
}
```

Amb un error tipogràfic (`brand.contry`), l'aplicació no arrenca:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - unknown attribute 'contry' of BrandEntity in path 'brand.contry'
```

`ProjectionPlanValidatorTest` cobreix el pla de la demo, un tipus que no és entitat, atributs i identificador desconeguts, camins que acaben en una associació, camins que travessen col·leccions o atributs bàsics, col·leccions inexistents o que no ho són, i que l'executor no consulta amb un pla invàlid.

---

## 7. Fase 3: pla de projecció derivat de les anotacions

**Estat: implementada a la branca `feature/projeccio`.**

**Objectiu:** no mantenir el `ProjectionPlan` a mà. Les seccions de la projecció surten de les mateixes anotacions que fa servir la serialització, de manera que no poden divergir.

### 7.1. Ús

Només cal indicar l'atribut de l'entitat quan el nom no coincideix amb el de la propietat:

```java
@ModelBasic
@ModelSource("constructionYear")
private int construction;
```

L'executor deriva el pla, el valida i el desa (una vegada per parell model–entitat):

```java
ProjectionPlan<VehicleEntity> plan = executor.planFor(VehicleModelBean.class, VehicleEntity.class);
```

`VehicleProjection.plan(executor)` encapsula aquesta crida. La configuració de la demo la fa en crear el bean `ProjectionExecutor`, de manera que un model que no encaixi amb les entitats atura l'arrencada. Per exemple, si es treu el `@ModelSource` anterior:

```
Invalid projection plan for cat.albirar.dynmod.demo.entity.VehicleEntity:
 - property VehicleModelBean.construction: unknown attribute 'construction' of VehicleEntity (use @ModelSource if the name differs)
```

`ProjectionPlan.fromModel(metamodel, model, entitat)` dona el pla sense validar, per a qui el vulgui fer servir fora de l'executor.

### 7.2. Regles de derivació

**Secció de cada propietat** (les mateixes regles que el filtre de serialització):

| Propietat | Es llegeix |
| --- | --- |
| `@ModelBasic`, o `@ModelSections` sense seccions | Sempre que es llegeix el model que la conté |
| `@ModelSections(X, Y)` | Si se selecciona `X` o `Y`, juntament amb les seccions de les propietats que la contenen |
| Sense marca, en un model amb marques | Només amb la selecció completa |
| Qualsevol propietat d'un model sense marques | Sempre que es llegeix el model |

**Tipus de l'atribut d'origen** (`@ModelSource` o el nom de la propietat):

| Atribut de l'entitat | Resultat |
| --- | --- |
| Bàsic | Una columna |
| `ManyToOne`, `OneToOne` o `Embedded` | Les columnes del model niat (també dins d'un `Optional`), amb el prefix de l'associació |
| Col·lecció | Una col·lecció del pla. L'element és el valor d'un `Map`, l'element d'una `Collection` o d'un array |

Les propietats amb `@JsonIgnore`, `static` o `transient` s'ignoren. Les anotacions es busquen al camp i, si no hi són, al getter, com fa la serialització.

### 7.3. Generalització de `ProjectionColumns`

Per poder representar qualsevol model, `ProjectionColumns` admet ara:

- **`sections(List.of(BRAND, ECONOMIC), ...)`**: columnes que només es llegeixen si totes les seccions estan seleccionades. És el cas d'una secció dins d'un model niat per una associació `ManyToOne`.
- **`fullOnly(...)`**: columnes que només es llegeixen amb la selecció completa, per a les propietats sense marca d'un model marcat.

`ProjectionPlan.toString()` i `ProjectionColumns.toString()` descriuen el pla en ordre canònic. Dos plans equivalents es descriuen igual, cosa que permet comparar-los a les proves.

### 7.4. Proves afegides

| Prova | Què verifica |
| --- | --- |
| `ModelPlanDeriverTest` | El pla derivat de `VehicleModelBean` és idèntic al que s'havia escrit a mà a la fase 2. Cobreix també: `@ModelSource`, models niats amb i sense marques, `Optional`, propietats en dues seccions, propietats sense marca, `@JsonIgnore`, `Map` i `List` com a col·leccions, i els errors (atributs desconeguts, orígens que travessen col·leccions, col·leccions sense secció única, element desconegut, col·leccions niades, tipus que no és entitat). També que l'executor deriva cada pla una sola vegada i el valida. |
| `ProjectionColumnsTest` | Seccions niades, columnes només per a la selecció completa i descripció canònica. |

Les proves d'equivalència i de SQL de la fase 2 passen sense canvis amb el pla derivat.

### 7.5. Abast i limitacions

- **L'assembler continua essent manual.** `VehicleProjection.toBean` converteix els valors en beans. Les diferències de forma entre entitat i bean (`List` → `Map` amb clau, `String` → `Currency`, enums duplicats, `Optional`) fan que automatitzar-lo s'acosti a un mini-ORM. La fase 3 automatitza el que podia divergir de les anotacions (quines columnes es llegeixen per a cada secció) i deixa explícita la conversió.
- **Col·leccions.** Només com a propietats directes del model arrel, i en una sola secció, perquè l'executor les llegeix amb una consulta per col·lecció des de l'arrel.
- **Propietats calculades.** Una propietat sense atribut a l'entitat és un error de derivació. Si cal, s'ha de marcar amb `@JsonIgnore` o calcular fora del model projectat.
- **Descobriment per camps.** Les propietats es descobreixen pels camps del model i de les seves superclasses. Un model sense camps (només getters) no es pot derivar.

---

## 8. Estratègia de proves

| Tipus | Què verifica | Estat |
| --- | --- | --- |
| **Equivalència** | Les E2E existents passen sense canvis i les respostes són idèntiques a les d'abans de la projecció. | ✅ Fase 1 |
| **Cost** | Amb `hibernate.generate_statistics=true`, el nombre de sentències per selecció correspon a la taula de la fase 1. | ✅ Fase 1 (`VehicleProjectionTest`) |
| **Guardrail** | Amb `open-in-view=false`, cap prova provoca `LazyInitializationException`. | ✅ Fase 1 |
| **Absència de columnes** | El SQL de cada selecció no conté les taules ni les columnes de seccions no seleccionades. | ✅ Fase 2 (`VehicleProjectionTest`) |
| **Equivalència entre fases** | Projecció per columnes i càrrega d'entitats produeixen el mateix JSON filtrat. | ✅ Fase 2 (`VehicleProjectionEquivalenceTest`) |

---

## 9. Resum i recomanació

| Fase | Abast | Guany | Complexitat | Estat |
| --- | --- | --- | --- | --- |
| 0 | Selecció resolta abans del controlador | Validació sense tocar la BD; base per a la resta | Baixa | ✅ Implementada |
| 1 | Associacions per secció, sense N+1 | 61 → 1–3 consultes | Mitjana | ✅ Implementada |
| 2 | Columnes per secció (inclosa `E`) | Menys dades llegides per fila; sense entitats | Mitjana-alta | ✅ Implementada |
| 3 | Pla derivat de les anotacions | Sense pla manual; projecció i serialització no poden divergir | Alta | ✅ Implementada |

Amb les **quatre fases** la proposta queda completa: la selecció del client redueix la resposta, el nombre de consultes (de 61 a entre 0 i 3) i les columnes llegides, sense canviar el contracte. El pla de projecció es deriva de les mateixes anotacions que la serialització i es valida a l'arrencada. Les respostes són idèntiques a les d'abans, cosa que verifiquen les proves E2E i la d'equivalència.

L'única part que queda escrita a mà per recurs és l'assembler de files a beans (apartat 7.5).
