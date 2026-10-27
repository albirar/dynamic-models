# Models dinàmics en API REST

Prova de concepte d'una API REST en què **el client decideix quines seccions del model vol rebre**, amb codis compactes enviats com a query parameter o header:

```http
GET /vehicles?m=BP              → dades bàsiques + marca (B) + peces (P)
GET /vehicles                   → model complet, com qualsevol API
X-Model-Sections: M             → dades bàsiques + dades de mercat, via header
```

El servei manté **un únic model de resposta** per recurs. La selecció s'aplica durant la serialització JSON, sense duplicar DTO, endpoints ni consultes.

Stack: Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3), Spring Data JPA, H2.

---

## Per què

La majoria d'API REST retornen sempre el model sencer. És senzill, però a mesura que els models creixen apareixen problemes coneguts:

- **Over-fetching**: una pantalla de llistat que només mostra nom i tipus rep també preus, mercats i peces de cada element.
- **Proliferació d'endpoints o DTO**: per evitar-ho es creen variants (`/vehicles/summary`, `VehicleLightDto`, `VehicleDetailDto`...) que s'han de mantenir, documentar i versionar per separat.
- **Cost de xarxa i de parsing**: rellevant en clients mòbils, en tràfic entre centres de dades i en APIs públiques amb cost de sortida.

Aquest projecte proposa un punt intermedi entre "sempre tot" i un llenguatge de consulta complet com GraphQL: el model es divideix en **seccions amb nom** que el client pot demanar o ometre.

## Com funciona

### 1. Marcar el model

```java
public class VehicleModelBean {
    @ModelBasic                              // sempre inclòs
    private String model;

    @ModelSections(ModelPart.BRAND)          // inclòs si es demana B
    private BrandBean brand;

    @ModelSections(ModelPart.MARKET)         // inclòs si es demana M
    private Map<String, MarketDataBean> marketData;
}

public class MarketDataBean {
    @ModelBasic
    private String country;

    @ModelSections(ModelPart.ECONOMIC)       // preus només si es demana E
    private float marketPrice;
}
```

### 2. Activar-ho a l'endpoint

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

### 3. Demanar seccions des del client

| Codi | Secció | Contingut |
| --- | --- | --- |
| `B` | `BRAND` | Marca del vehicle |
| `M` | `MARKET` | Dades de comercialització per país |
| `P` | `PARTS` | Peces del vehicle |
| `E` | `ECONOMIC` | Preus i marges (dins de mercats i peces) |

| Petició | Resultat |
| --- | --- |
| Sense `m` ni header | Model complet (comportament habitual) |
| `?m=` (buit) | Només dades bàsiques |
| `?m=BP` | Bàsiques + marca + peces, sense preus |
| `?m=PE` | Bàsiques + peces amb preus |
| `X-Model-Sections: BP` | Igual que `?m=BP` |
| `?m=M` + `X-Model-Sections: B` | El query parameter té preferència: només `M` |
| `?m=BX`, `?m=b`, `?m=B,P`, `?m=B&m=P` | HTTP 400 |

Regles:

- Els codis van **concatenats**, distingeixen majúscules i els repetits s'ignoren (`BBP` = `BP`).
- La selecció **s'aplica en cascada**: `E` afecta els preus de mercats i de peces, però només si `M` o `P` hi són. Un fill no pot fer aparèixer un pare exclòs.
- Si el bean té alguna marca, les propietats **sense marca s'ometen** quan hi ha selecció. Un bean sense cap marca (per exemple `BrandBean` o els embolcalls de paginació) es serialitza sempre complet.
- Header i query parameter es poden fer servir indistintament. **Només quan hi són tots dos**, mana el query parameter i el header s'ignora sense validar-lo. Això permet que un client fixi una selecció per defecte al header i la substitueixi en peticions concretes.
- Els endpoints filtrats responen sempre amb `Vary: X-Model-Sections`, perquè els caches HTTP no barregin respostes.
- Les regles habituals de Jackson (`@JsonInclude`, `@JsonIgnore`, `@JsonFormat`, vistes, filtres propis) continuen vigents.

### Exemple real

Amb les 25 dades de vehicles de la demo (`GET /vehicles?size=100`):

| Selecció | Mida de la resposta |
| --- | --- |
| Model complet | 7.817 bytes |
| `m=B` | 6.156 bytes (−21 %) |
| `m=` | 3.234 bytes (−59 %) |

En models reals, amb col·leccions grans (historials, línies, documents), la diferència sol ser molt més gran.

---

## Aplicació en una API REST en producció

### Casos on aporta valor

- **Llistats i detalls amb el mateix recurs.** Un llistat demana `m=` i la vista de detall demana `m=BMP`, sense mantenir dos DTO ni dos endpoints.
- **Clients heterogenis.** L'app mòbil, el web i les integracions de tercers consumeixen el mateix endpoint amb seleccions diferents.
- **Dades sensibles al cost de transferència.** Xarxes mòbils, sortida de núvol facturada, tràfic entre regions.
- **Backends for Frontends i gateways** que agreguen diverses API i només necessiten una part de cada resposta.
- **Evolució del model sense trencar clients.** Afegir una secció nova no canvia la resposta dels clients que ja envien una selecció. Els que no n'envien rebran el camp nou, com en qualsevol API.

### Com desplegar-ho

1. **Opt-in gradual.** La infraestructura s'activa amb `models.filtratge=true` i cada endpoint amb `@ModelFiltering`. Sense selecció la resposta és idèntica a l'anterior, de manera que els clients existents no se n'adonen.
2. **Publicar el contracte.** L'anotació no genera OpenAPI per si sola. Cal documentar, per endpoint, el query parameter, el header i les seccions disponibles amb els seus codis. Els codis són part del contracte: no es poden reutilitzar ni canviar de significat.
3. **Caches i CDN.** El query parameter normalment ja forma part de la clau de cache. Per al header, cal comprovar que el CDN o el proxy respecten `Vary` (o incloure el header a la clau de cache de manera explícita).
4. **CORS.** Si un navegador ha d'enviar `X-Model-Sections`, cal afegir-lo a `Access-Control-Allow-Headers`.
5. **Observabilitat.** Monitorar errors 400 per selecció invàlida (clients mal configurats) i la mida de les respostes per selecció, per confirmar el benefici.
6. **Proves de fum després de cada desplegament.** `scripts/smoke-test.sh <URL>` comprova que l'API respon i que el filtratge està actiu.

### Què no és

- **No és un mecanisme d'autorització.** Sense selecció es retorna el model complet; tot el que el model pot contenir ha d'estar ja autoritzat per a l'usuari. Per amagar dades sensibles cal filtrar-les abans, a la capa de servei.
- **No és una projecció de consulta.** El filtratge es fa en serialitzar: la base de dades i el mapatge a DTO fan la mateixa feina amb o sense selecció.

---

## Avantatges i inconvenients davant de retornar sempre el model complet

### Avantatges

| | |
| --- | --- |
| **Menys dades transferides** | Els clients reben només el que fan servir. Menys latència, menys cost de xarxa i menys parsing al client. |
| **Un sol model i un sol endpoint** | Evita la proliferació de DTO "light", "summary" i "detail" i d'endpoints paral·lels que s'han de mantenir sincronitzats. |
| **Compatible amb els clients existents** | Sense selecció el comportament és exactament el d'abans. Es pot adoptar endpoint a endpoint i client a client. |
| **Contracte simple** | Codis d'una lletra, fàcils d'enviar des de qualsevol client HTTP, sense llenguatge de consulta ni biblioteca de client especial. |
| **Seccions estables i amb sentit de negoci** | El client demana "dades de mercat", no una llista de camps concrets. El servidor pot reorganitzar camps dins d'una secció sense trencar el contracte. |
| **Compatible amb caches HTTP** | Les respostes són GET normals amb `Vary`; no cal canviar d'infraestructura com passaria amb GraphQL via POST. |
| **Validació estricta** | Un codi desconegut és un 400 explícit, no una resposta silenciosament incompleta. |

### Inconvenients

| | |
| --- | --- |
| **No estalvia treball al servidor** | Les dades es carreguen i es mapegen igualment. Si el cost principal és la base de dades o un servei remot, cal combinar-ho amb projeccions o càrrega lazy per secció. |
| **Contracte més complex** | Cada camp del model pot ser present o no segons la petició. Els clients tipats han de tractar com a opcionals tots els camps que no són bàsics, i la documentació ha d'explicar les seccions. |
| **OpenAPI no ho expressa** | Les eines de generació de clients no saben quins camps depenen de cada secció; cal documentar-ho a mà o amb extensions pròpies. |
| **Fragmentació de cache** | Cada combinació de seccions és una entrada diferent. Amb 4 seccions hi ha fins a 16 variants per URL. |
| **Granularitat limitada** | Les seccions són de gra gruixut: no es pot demanar un camp solt. La mateixa selecció s'aplica a tots els nivells del model. |
| **Espai de codis finit** | Una lletra per secció. N'hi ha prou per a desenes de seccions, però s'han de governar i no es poden reciclar. |
| **Cal disciplina en els DTO** | En marcar un bean, tot camp nou sense marca desapareix de les respostes filtrades. Els tests de contracte n'han de protegir la regressió. |
| **Més combinacions a provar** | El comportament depèn de la selecció; les proves han de cobrir com a mínim el model complet, el bàsic i cada secció. |

### Davant d'altres alternatives

| Alternativa | Comparació |
| --- | --- |
| **Model complet sempre** | Més simple, més previsible i millor suportat per les eines; acceptable mentre els models són petits o els clients són homogenis. |
| **Endpoints o DTO específics** | Contracte explícit i tipat, però multiplica codi i documentació a cada nova necessitat de client. |
| **Sparse fieldsets** (`fields=a,b` a JSON:API o les respostes parcials de Google) | Granularitat per camp, més flexible però més verbós, més difícil de cachejar i acobla el client als noms interns dels camps. |
| **GraphQL** | Selecció arbitrària i tipada, i resol també la càrrega de dades. Requereix un altre stack, un altre model de cache i un altre contracte. |

**Quan fer-lo servir:** models amb blocs de dades clarament separables, clients amb necessitats diferents sobre el mateix recurs i una API REST que es vol mantenir. **Quan no:** models petits, un únic client, o quan el cost real és a la base de dades i no a la resposta.

---

## Arquitectura

```
client ─HTTP─► Spring MVC ─► controller ─► servei ─► JPA
                   │
                   ▼
         ModelFilteringAdvice        llegeix ?m o X-Model-Sections, valida (400), afegeix Vary
                   │                 i passa la selecció com a hint d'escriptura
                   ▼
  JacksonJsonHttpMessageConverter    còpia privada del JsonMapper de Boot + ModelFilteringModule
                   │
                   ▼
   PropertyFilter per resposta       inclou @ModelBasic i les @ModelSections seleccionades,
                                     en cascada per tot el graf
```

- **Sense estat compartit**: la selecció és un objecte immutable per resposta; no hi ha `ThreadLocal`.
- **Aïllat**: només el converter JSON de servidor fa servir el mapper amb el mòdul; el `JsonMapper` de l'aplicació i els clients HTTP no canvien.
- **Infraestructura com a biblioteca**: el paquet `cat.albirar.dynmod.filtering` no depèn de l'aplicació i es carrega per auto-configuració.

Detalls a:

- [Arquitectura de filtratge de models](src/main/docs/arquitectura-models-dinamics.md): contracte i disseny.
- [Nova implementació](src/main/docs/nova-implementacio.md): adaptació a Spring Boot 4 / Jackson 3 i migració des de la versió anterior.

### Estructura

```
src/main/java/cat/albirar/dynmod/
├── filtering/                    infraestructura reutilitzable
│   ├── model/                    ModelPart, @ModelBasic, @ModelSections, ModelSelection
│   ├── jackson/                  mòdul, introspector, filtre i proveïdor de filtres
│   └── web/                      @ModelFiltering, advice, auto-configuració
└── demo/                         aplicació d'exemple (vehicles i marques)
scripts/smoke-test.sh             proves de fum contra una instància desplegada
```

---

## Execució

Requisits: JDK 21 i Maven.

```bash
mvn spring-boot:run
```

| URL | |
| --- | --- |
| `http://localhost:8080/vehicles?m=BP` | Vehicles amb marca i peces |
| `http://localhost:8080/brands` | Marques |
| `http://localhost:8080/swagger-ui.html` | Documentació OpenAPI |

Paràmetres de cerca de `/vehicles`: `brandManufacturerWMI`, `country`, `type`, `commercialNamePattern`, i paginació estàndard (`page`, `size`, `sort`).

## Proves

```bash
mvn test                        # totes: unitàries, integració, E2E i fum
mvn test -Dgroups=e2e           # E2E sobre un servidor HTTP real
mvn test -Dgroups=smoke         # proves de fum
scripts/smoke-test.sh http://localhost:8080   # fum contra una instància en marxa
```

| Nivell | Què cobreix |
| --- | --- |
| Unitàries | Parser de seleccions; mòdul Jackson (marques, cascada, configuracions incorrectes, concurrència) |
| Integració | Condicions d'auto-configuració; context Boot real amb `MockMvc` (formes de resposta, header i query parameter, preferència, `Vary`, errors, herència d'anotacions) |
| E2E | Servidor HTTP real amb les dades de `data.sql`: seccions i cascada, totes les seccions equivalen al model complet, header equivalent al query parameter, preferència, errors 400, cerca i paginació, peticions concurrents |
| Fum | L'aplicació arrenca, els endpoints principals responen, el filtratge està actiu i l'OpenAPI es publica |


