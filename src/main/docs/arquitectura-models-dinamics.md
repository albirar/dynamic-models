# Arquitectura de filtratge de models en respostes REST

## 1. Objectiu i abast

Aquest document descriu una arquitectura reutilitzable per permetre que un consumidor REST seleccioni seccions d'un DTO JSON mitjançant codis compactes. El servei manté un únic model de resposta; la selecció s'aplica durant la serialització.

L'arquitectura assumeix Spring MVC i Jackson, compatible amb Java 11 i Spring Boot 2.5.x. No depèn d'una forma concreta de controller: funciona amb un objecte, `ResponseEntity`, col·leccions, arrays, mapes, embolcalls de paginació i valors paginats de Spring Data.

> **Spring Boot 4 / Spring Framework 7 / Jackson 3:** el contracte observable (seccions 2, 4, 7 i 9) es manté, però el mecanisme intern de les seccions 3.3–3.6 canvia. Vegeu la [secció 11](#11-adaptació-a-spring-boot-4-spring-framework-7-i-jackson-3) i [nova-implementacio.md](./nova-implementacio.md).

El mecanisme és una política de presentació de resposta, no un sistema d'autorització ni una projecció de consulta. No s'ha d'utilitzar per protegir informació sensible ni per reduir consultes a base de dades.

## 2. Contracte observable

Calen dues activacions independents:

1. La propietat global `models.filtratge=true` registra la infraestructura MVC.
2. L'endpoint o el controlador declara `@ModelFiltering` per optar-hi.

En un endpoint activat:

| Selecció | Comportament |
| --- | --- |
| Absent del query parameter i del header | No hi ha filtratge; Jackson retorna el model complet habitual. |
| Present i buit | Només s'inclouen propietats `@ModelBasic` i `@ModelSections` buides. |
| Un o més codis vàlids | S'inclouen les dades bàsiques i les seccions coincidents. |
| Codi desconegut o valor repetit | Error HTTP 400. |

Un endpoint sense `@ModelFiltering` no consulta la selecció i conserva el comportament normal, encara que la petició contingui un codi incorrecte.

La selecció es pot enviar com a query parameter o com a header. Si la petició porta tots dos, el query parameter té preferència i el header s'ignora completament (ni s'aplica ni es valida). El header només s'utilitza quan el query parameter és absent.

Les regles existents de Jackson continuen vigents: per exemple, `NON_NULL`, `JsonIgnore`, vistes, noms de propietats i filtres addicionals. Una propietat bàsica amb valor nul pot continuar omesa per `NON_NULL`.

## 3. Components i responsabilitats

### 3.1 Contracte d'anotacions del model

Les anotacions públiques s'han de mantenir independents de Spring MVC per permetre que els DTO s'utilitzin en clients, missatgeria o altres mòduls sense arrossegar el runtime web.

- `ModelPart`: enumerat de seccions, cada constant amb nom explícit, codi estable d'una lletra i descripció.
- `ModelBasic`: marca una propietat que sempre s'inclou en una resposta filtrada.
- `ModelSections`: associa una propietat a una o més seccions opcionals.
- `ModelFiltering`: opt-in per classe o mètode; permet canviar el nom del query parameter (`queryParam`, per defecte `m`) i del header (`header`, per defecte `X-Model-Sections`).

Regla de combinació de marques d'una propietat:

| Marca | Selecció explícita activa |
| --- | --- |
| `@ModelBasic` | Inclosa sempre. |
| `@ModelSections(A, B)` | Inclosa quan la selecció conté `A` **o** `B`. |
| `@ModelSections` sense codis | Inclosa incondicionalment; es recomana `@ModelBasic` per a camps bàsics. |
| Cap marca, en un bean amb alguna marca | Omesa. |
| Cap marca en tot el bean | Es conserva la serialització de totes les propietats. |

No s'han de combinar `@ModelBasic` i `@ModelSections` en una propietat. La implementació ha de detectar aquesta configuració incorrecta i fallar clarament.

Exemple:

```java
public class PersonaResponse {
    @ModelBasic
    private String id;

    @ModelSections(ModelPart.CONTACTE)
    private List<ContacteResponse> contactes;

    @ModelSections({ModelPart.CONTACTE, ModelPart.RELACIONS})
    private String idiomaComunicacio;
}
```

Les marques s'apliquen a propietats Jackson, no a noms JSON textuals independents. Cal establir convenció d'anotació de camp o d'accessor i no donar-hi metadades contradictòries. Si s'introdueix la primera marca en un DTO, revisar totes les seves propietats: les que quedin sense marca passaran a ser omeses quan el filtre s'activi.

### 3.2 `ModelSelection`

És un valor immutable que transforma el text de la petició en un conjunt `EnumSet<ModelPart>` una sola vegada per resposta.

- Codis repetits dins un sol valor es dedupliquen (`ccr` equival a `cr`).
- Majúscules i minúscules no es normalitzen.
- Codis desconeguts produeixen una excepció de validació que l'advice tradueix a 400.
- No conté dependències HTTP ni estat mutable compartit.

El nom `m` o `X-Model-Sections` no forma part del parser; l'advice passa el valor extret des del transport.

### 3.3 `ModelFilteringAdvice`

Un `ResponseBodyAdvice` s'executa dins el flux de serialització MVC, també per a `ResponseEntity`. Ha de:

1. Limitar `supports()` als convertidors Jackson i als endpoints amb `@ModelFiltering`.
2. Resoldre primer l'anotació del mètode i, si no n'hi ha, la del tipus del controller. Fer servir les utilitats d'anotacions Spring perquè es contemplin metadades d'interfícies, mètodes implementats i superclasses.
3. Resoldre els noms del query parameter i del header.
4. Llegir el query parameter; només si és absent, llegir el header. Quan hi ha query parameter, el header no es valida.
5. Si el valor és absent, retornar el cos original sense embolcallar-lo. Això conserva completament el camí normal de Jackson.
6. Si és present, validar-lo i embolcallar el cos en un contenidor equivalent a `MappingJacksonValue` que transporti una instància immutable de `ModelSelection`.
7. No substituir una vista Jackson o `FilterProvider` que ja vinguin dins un `MappingJacksonValue`.
8. Preservar respostes d'error HTTP i afegir el nom del header a `Vary` a totes les respostes de l'endpoint. No eliminar valors `Vary` que ja hi siguin.

El header s'afegeix a `Vary` fins i tot si és absent a la petició: l'absència produeix el model complet, diferent d'un header present amb selecció. Això ajuda els caches HTTP a no reutilitzar una resposta incorrecta. Si `Vary: *` ja hi és, no cal afegir-hi res.

### 3.4 `ModelFilteringModule`

És un mòdul Jackson registrat en un `ObjectMapper` privat del convertidor MVC. Un `BeanSerializerModifier` inspecciona les propietats de cada bean:

1. Si cap propietat té `@ModelBasic` ni `@ModelSections`, deixa el bean intacte. Això és essencial per a embolcalls de paginació i beans d'infraestructura.
2. Si el bean té alguna marca, decora tots els seus `BeanPropertyWriter`, no només els anotats.
3. Sense un atribut de selecció al `SerializerProvider`, delega exactament a Jackson sense filtrar.
4. Amb selecció, inclou `@ModelBasic`, les seccions buides i les propietats amb almenys una secció seleccionada; omet les altres propietats, incloses les sense marca.
5. No serialitza explícitament els valors omesos: delega els valors inclosos al writer original, preservant `NON_NULL` i les altres regles Jackson.

La selecció s'emmagatzema com a atribut d'un `ObjectWriter` per resposta. En serialitzar una propietat imbricada, Jackson reutilitza el mateix `SerializerProvider`; per tant, el mateix conjunt de seccions arriba recursivament als fills, elements de llistes, arrays i valors de mapes. Un fill no pot tornar a incloure una propietat pare exclosa.

### 3.5 `ModelFilteringConverter`

Decora un `MappingJackson2HttpMessageConverter` existent durant l'inici MVC:

- Crea una còpia del `ObjectMapper` original i registra-hi el mòdul de filtratge.
- Delega les lectures al convertidor original.
- Delega les escriptures ordinàries al convertidor original.
- Només les respostes seleccionades es serialitzen amb `ObjectWriter` i atribut de selecció.
- Conserva la vista i els filtres d'un `MappingJacksonValue` preexistent.
- No canvia cap mapper compartit durant una petició.

Aquesta còpia localitza el filtratge a MVC; no canvia els mappers de Feign ni la serialització invocada directament a la resta de l'aplicació.

### 3.6 `ModelFilteringConfiguration`

La configuració només es registra quan coincideixen totes aquestes condicions:

- Classpath amb Spring MVC i el convertidor Jackson.
- Aplicació web servlet.
- `models.filtratge=true`.

En Spring Boot 2, l'auto-configuració s'anuncia a `META-INF/spring.factories`. Es pot oferir també importació explícita per a projectes amb auto-configuració pròpia. La configuració amplia els convertidors existents en lloc d'afegir un convertidor amb prioritat arbitrària.

En una biblioteca compartida, les dependències web/servlet han de ser opcionals i limitades al mòdul d'integració. Els consumidors no web han de poder carregar els DTO sense requerir Spring MVC.

## 4. Selecció HTTP

### Query parameter

Per defecte:

```http
GET /persones?m=cr
```

### Header

Per defecte:

```http
GET /persones
X-Model-Sections: cr
```

### Noms personalitzats

L'anotació pot canviar `queryParam` i `header`; cada nom substitueix completament el seu valor per defecte.

### Preferència

| Query parameter | Header | Selecció aplicada |
| --- | --- | --- |
| Absent | Absent | Cap: model complet |
| Absent | Present | La del header |
| Present | Absent | La del query parameter |
| Present | Present | La del query parameter; el header s'ignora |

Així, un client pot fixar una selecció per defecte amb el header (per exemple, en un interceptor HTTP) i substituir-la puntualment per a una petició concreta amb el query parameter.

### Validació

- Un sol valor per origen utilitzat. Es rebutgen `?m=c&m=r` i, quan s'utilitza el header, headers amb més d'un valor.
- Els codis del valor són concatenats, no separats per comes ni espais.
- Cada caràcter ha de correspondre exactament a un codi publicat.
- Codis repetits dins el valor són vàlids i s'eliminen en construir el conjunt.
- El valor buit és vàlid i significa cap secció opcional.
- Un codi conegut però sense camps al DTO d'aquest endpoint és vàlid i no afegeix camps.

## 5. Flux de resposta

```mermaid
sequenceDiagram
    participant C as Client REST
    participant M as Spring MVC
    participant A as ModelFilteringAdvice
    participant R as ModelSelection
    participant H as HttpMessageConverter
    participant J as Jackson / ModelFilteringModule

    C->>M: Petició a endpoint
    M->>A: Abans d'escriure el cos
    A->>A: Comprova anotació endpoint; llegeix query parameter o, si és absent, header
    alt Endpoint sense anotació o selecció absent
        A-->>H: Cos original, sense filtratge
        H->>J: Serialització normal
    else Selecció present
        A->>R: Parseja codis una vegada
        alt Codi invàlid o valor repetit
            R-->>A: Error de validació
            A-->>C: HTTP 400
        else Selecció vàlida
            R-->>A: ModelSelection immutable
            A-->>H: Cos embolcallat amb selecció
            H->>J: ObjectWriter amb atribut per resposta
            J->>J: Serialitza propietats i aplica marques recursivament
            J-->>C: JSON filtrat
        end
    end
```

## 6. Integració a un altre projecte

### 6.1 Dependències

1. Importar el paquet d'anotacions de model al mòdul DTO.
2. Afegir Spring MVC / Jackson al servei web. Si els components es publiquen en una biblioteca comuna, declarar les dependències web com a opcionals i separar-les d'un artefacte de DTO pur quan l'arquitectura ho permeti.
3. Verificar la compatibilitat binària de Spring i Jackson. El disseny exposat aquí és específic de Spring Boot 2/Spring Framework 5 i `javax.servlet`; Boot 3/Spring 6 requereix adaptar namespaces, registre d'auto-configuració i validar API Jackson.
4. Habilitar `models.filtratge=true` només als serveis que vulguin carregar la infraestructura.

### 6.2 Auto-configuració

Per Spring Boot 2:

```properties
models.filtratge=true
```

Registrar la configuració a `META-INF/spring.factories` sota `EnableAutoConfiguration`, i protegir-la amb condicions de classpath, servlet web i propietat. Si no s'utilitza Spring Boot o s'ha desactivat la descoberta, importar la configuració des de la configuració MVC de l'aplicació.

### 6.3 Opt-in d'endpoints

```java
@RestController
@RequestMapping("/persones")
@ModelFiltering
class PersonaController {
    @GetMapping
    List<PersonaResponse> list() { ... }

    @GetMapping("/{id}")
    @ModelFiltering(queryParam = "seccions", header = "X-Persona-Seccions")
    ResponseEntity<PersonaResponse> get() { ... }
}
```

La segona anotació substitueix la configuració de classe completa. Per tant, el segon endpoint llegeix `seccions` i `X-Persona-Seccions`, i ignora `m` i `X-Model-Sections`.

### 6.4 Serialitzadors Jackson preexistents

Abans d'integrar-ho, comprovar:

- Ordre i tipus dels convertidors registrats per Boot i per configuracions MVC pròpies.
- Mappers especialitzats per mitjà de contingut, vistes, filtres i mix-ins.
- Tipus de paginació i embolcalls personalitzats que es volen conservar.
- Respostes amb formats diferents de JSON, streaming, `Resource`, `String`, XML o missatges asíncrons.

El decorator s'ha d'aplicar només als convertidors Jackson. Cal provar que la delegació preserva els detalls necessaris del convertidor de l'aplicació (prefix/sufix JSON, charset, media types o configuració específica). Si aquests casos són rellevants, implementar una delegació completa o registrar el mòdul al mapper existent en un punt únic i immòbil, sense mutar-lo durant les peticions.

## 7. Seguretat i caches

El filtratge només treu propietats de la representació. No reemplaça comprovacions d'autorització ni redacció obligatòria de dades. En particular, si `m` és absent, es retorna el model complet de l'endpoint; el servei ha d'autoritzar prèviament tot el contingut que pot retornar.

Retornar `Vary: <header>` a tots els endpoints filtrats i configurar CORS si el navegador ha d'enviar el header. Revisar també caches a CDN, proxy i aplicació. Query parameters normalment formen part de la clau de cache, però s'ha de comprovar la configuració concreta.

L'estat de selecció no es desa en un camp singleton, `ThreadLocal`, `RequestContextHolder` ni en l'`ObjectMapper`. Es construeix per resposta i s'aplica via `ObjectWriter`, evitant que peticions concurrents es filtrin mútuament.

## 8. Pla de proves mínim

### Parser i anotacions

- Codis únics, explícits i estables; codi vàlid, desconegut, minúscula i majúscula.
- Duplicats dins un valor, valor buit i ocurrències repetides.
- Propietat bàsica, una secció, més d'una secció (OR), marca buida i cap marca.
- Rebutjar `@ModelBasic` combinat amb `@ModelSections`.

### Advice i contracte endpoint

- Sense propietat, propietat falsa i propietat certa.
- Endpoint sense anotació (també amb codis invàlids) i endpoint anotat.
- Anotació a mètode, classe, interfície, mètode d'interfície i superclasse.
- Mètode que reemplaça tota la configuració de classe.
- Query parameter, header, preferència del query parameter quan hi són tots dos, header invàlid ignorat si hi ha query parameter, noms personalitzats, absència, buit i valor repetit.
- Resposta d'error preservada; `Vary` acumulatiu per header.

### Jackson i estructura

- POJO únic, `ResponseEntity`, llista, array, mapa, embolcall propi i `Page`/`PageImpl`.
- Llistes niades i diversos nivells de DTO filtrats recursivament.
- Bean intern sense marques, bean marcat parcialment i propietats bàsiques nul·les.
- `NON_NULL`, `JsonIgnore`, vistes, `FilterProvider` ja existent, dates i mòduls de tipus específics.
- Mapper de Feign o mapper extern inalterat.
- Peticions concurrents amb seleccions diferents, sense contaminació entre respostes.

### Context de Boot real

A més de `MockMvcBuilders.standaloneSetup`, aixecar un context real amb autoconfiguració per validar la descoberta via `spring.factories`, l'ordre de convertidors i coexistència amb les configuracions MVC de l'aplicació.

## 9. Limitacions i decisions conscients

- La selecció té semàntica OR quan una propietat declara diverses seccions.
- La propietat pare s'ha de seleccionar perquè s'arribi a serialitzar el seu objecte fill. Els codis del fill no poden forçar-ne la inclusió.
- Un bean sense marques conserva totes les propietats. Per això els DTO de domini que necessiten filtratge han de tenir almenys una marca; els wrappers tècnics es deixen sense marques.
- La cascada aplica els mateixos codis a tots els nivells. Si cal un contracte diferent per endpoint o per nivell, el parser/model de selecció s'ha d'ampliar deliberadament.
- El filtratge ocorre després de carregar i mapar la resposta; no estalvia IO ni processament.
- L'anotació no genera documentació OpenAPI per si sola. Cal publicar, per endpoint, els noms del query parameter i del header i les seccions realment disponibles.
- S'ha de mantenir una política explícita de compatibilitat per a canvis de codis, DTO compartits i versions de Spring/Jackson.

## 10. Guia de desplegament

1. Publicar la versió de la llibreria que conté anotacions i components.
2. Actualitzar primer els serveis consumidors sense activar la propietat; comprovar que els DTO es carreguen en contextos no web.
3. Afegir marques a DTO i proves de contracte.
4. Afegir `@ModelFiltering` només als endpoints aprovats.
5. Activar la propietat en un entorn controlat i provar clients, OpenAPI, caches i CORS.
6. Comparar respostes completes sense selecció amb la versió anterior: han de ser idèntiques segons les regles Jackson existents.
7. Activar gradualment els clients amb query o header i monitoritzar errors 400 i mida de resposta.
8. No habilitar `models.filtratge` globalment fins que s'hagi comprovat l'ordre i la coexistència dels convertidors del servei.

## 11. Adaptació a Spring Boot 4, Spring Framework 7 i Jackson 3

Les seccions 3.3–3.6 descriuen el mecanisme per a Spring Boot 2 / Jackson 2. A Spring Boot 4.x el convertidor JSON de MVC és `JacksonJsonHttpMessageConverter`, basat en Jackson 3 (`tools.jackson`), i diverses peces del disseny original ja no són aplicables:

| Disseny original | Problema a Boot 4 | Substitució |
| --- | --- | --- |
| Embolcallar el cos en un contenidor equivalent a `MappingJacksonValue` | `MappingJacksonValue` està deprecated i el convertidor de Jackson 3 el rebutja | `ResponseBodyAdvice.determineWriteHints` retorna un hint `FilterProvider` per resposta |
| Atribut de selecció en un `ObjectWriter` | `customizeWriter` del convertidor no rep els hints; caldria duplicar `writeInternal` | El `FilterProvider` del hint porta la selecció; Spring l'aplica amb `ObjectWriter.with(filters)` |
| `BeanSerializerModifier` que decora els `BeanPropertyWriter` | API renombrada (`ValueSerializerModifier`) | `AnnotationIntrospector.findFilterId` assigna un filtre només als beans marcats; un `PropertyFilter` immutable decideix cada propietat |
| Decorar `MappingJackson2HttpMessageConverter` | Deprecated (Jackson 2) | `ServerHttpMessageConvertersCustomizer` que registra un `JacksonJsonHttpMessageConverter` amb `jsonMapper.rebuild()` + mòdul |
| `META-INF/spring.factories` | Format retirat per a auto-configuracions | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `javax.servlet` | — | `jakarta.servlet` |

Conseqüències sobre els components:

- **`ModelFilteringAdvice`**: a `beforeBodyWrite` llegeix el query parameter o, si és absent, el header; valida la selecció, afegeix `Vary` i desa la `ModelSelection` com a atribut de la petició (Spring no passa la petició a `determineWriteHints`). A `determineWriteHints` construeix el `FilterProvider` immutable de la resposta. L'estat queda lligat a la petició, no a cap singleton, `ThreadLocal` propi ni mapper. Ha de portar `@ControllerAdvice` a la classe, de manera que el paquet de la biblioteca no ha de quedar dins l'escaneig de components del consumidor.
- **`ModelFilteringModule`**: registra l'introspector. Els beans sense marques no reben cap filtre (punt 1 de la secció 3.4). La còpia privada del mapper té un `FilterProvider` per defecte que deixa passar totes les propietats, de manera que sense hint la sortida és idèntica a la de Jackson (punt 3). El filtre del hint delega els ids que no són propis al `FilterProvider` existent del mapper, per no trencar `@JsonFilter` de l'aplicació.
- **Cascada**: el `FilterProvider` és el mateix per a tot el graf de l'`ObjectWriter`, de manera que la selecció arriba igualment a fills, llistes, arrays i mapes.
- **Mapper privat**: el bean `JsonMapper` de Boot i els convertidors de client no canvien; només el convertidor JSON de servidor fa servir la còpia amb el mòdul.
- **Configuració incorrecta**: un bean marcat amb `@JsonFilter` propi, o una propietat amb `@ModelBasic` i `@ModelSections`, falla en construir el serialitzador amb un missatge que identifica la classe i el membre.
- **Altres hints**: si un altre `ResponseBodyAdvice` aporta també un hint `FilterProvider`, Spring en conserva només un (l'últim). Cal que no hi hagi dos advices competint pel mateix hint en un endpoint filtrat.
