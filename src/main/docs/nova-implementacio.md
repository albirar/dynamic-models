# Nova Implementació de Filtratge de Models - Anàlisi i Proposta

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

El projecte actual té una implementació de filtratge de models basat en:
- `@FilteredView` als camps
- `FilterInterceptor` (HandlerInterceptor)
- `FilteredViewPropertyFilter` (SimpleBeanPropertyFilter)
- `FilterContext` (ThreadLocal)
- `@JsonFilter` als models

El document [arquitectura-models-dinamics.md](./arquitectura-models-dinamics.md) proposa una arquitectura alternativa basat en:
- `@ModelBasic` i `@ModelSections` als camps
- `ModelFilteringAdvice` (ResponseBodyAdvice)
- `ModelFilteringModule` (Jackson Module amb BeanSerializerModifier)
- `ModelSelection` (immutable, sense estat)

### 1.2. Objectiu

Aquest document analitza les diferències entre ambdues implementacions, avalua la compatibilitat amb Spring Boot 4.x, i proposa una implementació detallada de la nova arquitectura.

---

## 2. Comparació entre Implementacions

### 2.1. Comparativa General

| **Aspecte** | **Implementació Actual** | **Arquitectura Proposada** | **Valoració** |
|-------------|--------------------------|---------------------------|---------------|
| **Mecanisme Principal** | Interceptor (preHandle) | ResponseBodyAdvice (abans d'escriure) | ✅ Proposada més natural |
| **Estat** | ThreadLocal (FilterContext) | Atribut de ObjectWriter per resposta | ✅ Proposada més segura |
| **Validació** | Ignora codis invàlids (WARN) | Retorna HTTP 400 | ✅ Proposada més estricta |
| **Format Codis** | Separats per comes (`B,P,E`) | Concatenats (`BPE`) | ⚠️ Canvi d'API |
| **Anotacions** | `@FilteredView(filters={...})` | `@ModelBasic` + `@ModelSections(value={...})` | ✅ Proposada més expressiva |
| **Cascada** | Via ThreadLocal | Via SerializerProvider | ✅ Proposada més elegant |
| **Vary Header** | No implementat | Implementat per headers | ✅ Proposada millor per caches |
| **Aïllament** | Modifica ObjectMapper global | ObjectMapper privat per MVC | ✅ Proposada més aïllada |
| **Compatibilitat** | Spring Boot 4.x | Spring Boot 2.5.x (adaptable) | ✅ Amdues compatibles |

### 2.2. Comparativa de Components

#### Anotacions

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Propòsit** | `@FilteredView` als camps que es poden filtrar | `@ModelBasic` (sempre inclus) + `@ModelSections` (opcional) |
| **Comportament camps sense anotació** | Sempre inclus | **Exclosos** si el bean té alguna marca |
| **Validació combinacions** | No valida | **Rebutja** `@ModelBasic` + `@ModelSections` |
| **Flexibilitat** | Menys control | **Més control** (bàsic vs. seccions) |

**Exemple Actual:**
```java
@FilteredView(filters = {FiltersModel.Brand})
private BrandBean brand;
```

**Exemple Proposat:**
```java
@ModelBasic
private String id;

@ModelSections(ModelPart.BRAND)
private BrandBean brand;
```

#### Mecanisme de Filtratge

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Tecnologia** | `SimpleBeanPropertyFilter` + `@JsonFilter` | `BeanSerializerModifier` + `ModelFilteringModule` |
| **Integració** | Reemplaça el FilterProvider global | **Decora** el convertidor existent |
| **Impacte** | Pot afectar altres usuaris de l'ObjectMapper | **Aïlla** el filtratge a MVC |
| **Cascada** | Funciona via ThreadLocal | Funciona via `SerializerProvider` compartit |
| **Seguretat** | ThreadLocal pot causar problemes en entorns asíncrons | **No utilitza estat mutable** |

#### Configuració

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Propietat** | `spring.application.models.filtered` | `models.filtratge` |
| **Condicions** | Només propietat | Classpath + Servlet + Propietat |
| **Registre** | WebMvcConfigurer | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| **Abast** | Aplica a tot el context | **Més selectiu** |

#### Contracte HTTP

| **Aspecte** | **Actual** | **Proposada** |
|-------------|------------|---------------|
| **Paràmetre** | `m` (query) | `m` (query) |
| **Header** | `x-filter-model` | `X-Model-Sections` |
| **Fallback** | Paràmetre > Header | **No hi ha fallback** (exclusiu) |
| **Validació** | Ignora codis invàlids (WARN) | **Retorna 400** per codis invàlids |
| **Format** | Codis separats per comes (`B,P,E`) | **Codis concatenats** (`BPE`) |
| **Vary Header** | No implementat | **S'afegeix** `Vary: X-Model-Sections` |

### 2.3. Avantatges de la Implementació Actual

✅ **Més senzilla**: Menys components per mantenir
✅ **Ja implementada i funcionant**: No cal desenvolupar des de zero
✅ **Compatibilitat amb Spring Boot 4.x**: Ja adaptada
✅ **Format de codis més llegible**: `B,P,E` és més intuïtiu que `BPE`

### 2.4. Desavantatges de la Implementació Actual

❌ **Problemes de ThreadLocal**:
   - Pot causar memory leaks si no es neteja correctament
   - Problemàtic en entorns asíncrons (WebFlux, @Async)
   - Requerix `FilterContext.clear()` manual a `afterCompletion`

❌ **Menos aïllada**:
   - Modifica l'`ObjectMapper` global
   - Pot afectar altres parts de l'aplicació (Feign, tests, etc.)

❌ **Menos robusta**:
   - No valida combinacions d'anotacions
   - No distingeix entre camps bàsics i opcionals
   - Validació laxista (ignora codis invàlids en lloc de fallar)

❌ **Menos estàndard**:
   - No utilitza `ResponseBodyAdvice` (dissenyat per això)
   - No implementa `Vary` header (important per caches HTTP)

❌ **Menos mantenible**:
   - Menor separació de responsabilitats
   - Components més acoblats

### 2.5. Avantatges de l'Arquitectura Proposada

✅ **Més segura**:
   - No utilitza `ThreadLocal` (evita memory leaks)
   - L'estat de selecció és per resposta, no global
   - Millor comportament en entorns asíncrons

✅ **Més aïllada**:
   - No modifica l'`ObjectMapper` global
   - Només afecta als endpoints amb `@ModelFiltering`
   - No interfereix amb altres serialitzacions (Feign, etc.)

✅ **Més estàndard**:
   - Utilitza `ResponseBodyAdvice` (dissenyat per això)
   - Millor integració amb Spring MVC
   - Suport per `Vary` header (caches HTTP)

✅ **Més robusta**:
   - Validació estricta (400 per errors)
   - Distingeix entre camps bàsics i opcionals
   - Millor control del comportament

✅ **Més mantenible**:
   - Millor separació de responsabilitats
   - Components més modulars
   - Millor documentada

✅ **Més flexible**:
   - Permet personalitzar l'origen (query/header) per endpoint
   - Permet personalitzar el nom del paràmetre/header
   - Millor suport per a biblioteques reutilitzables

---

## 3. Anàlisi de Diferències de Versions

### 3.1. Spring Boot 2.5.x vs 4.1.1

#### Components de Spring

| **Component** | **Spring Boot 2.5.x** | **Spring Boot 4.1.1** | **Impacte** | **Solució** |
|---------------|----------------------|----------------------|-------------|-------------|
| **Servlet API** | `javax.servlet` | `jakarta.servlet` | ⚠️ Canvi de package | Utilitzar `jakarta.servlet` |
| **ResponseBodyAdvice** | Disponible | Disponible | ✅ Compatible | - |
| **MappingJackson2HttpMessageConverter** | Estable | **Deprecated** | ⚠️ Cal alternativa | Utilitzar `Jackson2ObjectMapperBuilder` |
| **ObjectMapper** | Estable | Estable | ✅ Compatible | - |
| **BeanSerializerModifier** | Disponible | Disponible | ✅ Compatible | - |
| **Auto-configuració** | `spring.factories` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | ⚠️ Canvi de format | Utilitzar nou format |
| **ConditionalOnProperty** | Disponible | Disponible | ✅ Compatible | - |

#### Components de Jackson

| **Component** | **Spring Boot 2.5.x** | **Spring Boot 4.1.1** | **Impacte** |
|---------------|----------------------|----------------------|-------------|
| **Versió** | ~2.12.x | ~2.17.x | ✅ Compatible (API estable) |
| **SimpleFilterProvider** | Disponible | Disponible | ✅ Compatible |
| **BeanSerializerModifier** | Disponible | Disponible | ✅ Compatible |
| **MappingJacksonValue** | Disponible | Disponible | ✅ Compatible |

### 3.2. Canvis Necessaris per Adaptar l'Arquitectura Proposada a Spring Boot 4.x

1. **Servlet API**: Canviar totes les referències de `javax.servlet` a `jakarta.servlet`
2. **Auto-configuració**: Utilitzar el nou format de Spring Boot 4.x:
   ```
   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
   ```
3. **HttpMessageConverter**: Evitar `MappingJackson2HttpMessageConverter` (deprecated) i utilitzar alternatives
4. **Dependencies**: Assegurar-se que totes les dependències són compatibles

### 3.3. Conclusió de Compatibilitat

✅ **El disseny del document és compatible amb Spring Boot 4.x** amb els següents ajusts:
- Canviar `javax.servlet` → `jakarta.servlet`
- Utilitzar el nou format d'auto-configuració
- Evitar classes deprecated

---

## 4. Proposta d'Implementació Detallada

### 4.1. Estructura de Paquets Proposada

```
src/main/java/cat/albirar/dynmod/demo/
├── filters/                    # Components de filtratge
│   ├── ModelPart.java         # Enumerat de seccions (B, M, P, E)
│   ├── ModelBasic.java        # Anotació per camps bàsics
│   ├── ModelSections.java     # Anotació per seccions opcionals
│   ├── ModelFiltering.java    # Anotació per endpoints
│   ├── ModelFilterSource.java # Enumerat: QUERY_PARAM, HEADER
│   ├── ModelSelection.java    # Parser de codis (immutable)
│   ├── ModelFilteringAdvice.java # ResponseBodyAdvice
│   ├── ModelFilteringModule.java # Mòdul Jackson
│   ├── ModelFilteringException.java # Excepció per errors 400
│   └── ModelFilteringConfiguration.java # Configuració
└── models/                   # Models existents (modificar anotacions)
```

### 4.2. Components a Implementar

#### 4.2.1. Anotacions (Independent de Spring MVC)

**ModelPart.java** - Enumerat de seccions:
```java
@Getter
public enum ModelPart {
    BRAND("B", "Brand information"),
    MARKET("M", "Market data"),
    PARTS("P", "Vehicle parts"),
    ECONOMIC("E", "Economic data");
    
    private final String code;
    private final String description;
    
    // Constructor, getters, fromCode() method
}
```

**ModelBasic.java** - Marca camps que sempre s'inclouen:
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface ModelBasic {}
```

**ModelSections.java** - Associa una propietat a seccions opcionals:
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface ModelSections {
    ModelPart[] value() default {};
}
```

**ModelFilterSource.java** - Origen de la selecció:
```java
public enum ModelFilterSource {
    QUERY_PARAM("m"),
    HEADER("X-Model-Sections");
    
    private final String defaultName;
    // Constructor, getter
}
```

**ModelFiltering.java** - Opt-in per endpoints:
```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ModelFiltering {
    ModelFilterSource source() default ModelFilterSource.QUERY_PARAM;
    String name() default "";
}
```

#### 4.2.2. ModelSelection (Parser de Codis)

```java
public final class ModelSelection {
    private final Set<ModelPart> parts;
    
    private ModelSelection(Set<ModelPart> parts) {
        this.parts = EnumSet.copyOf(parts);
    }
    
    public static ModelSelection of(String input) {
        // Validació i parsing
        // Llença IllegalArgumentException per codis invàlids
    }
    
    public boolean contains(ModelPart part) { ... }
    public Set<ModelPart> getParts() { ... }
    public boolean isEmpty() { ... }
}
```

**Característiques:**
- Immutable
- Validació estricta (400 per codis invàlids)
- Deduplicació de codis
- Sense estat mutable

#### 4.2.3. ModelFilteringModule (Mòdul Jackson)

```java
public class ModelFilteringModule extends SimpleModule {
    private static final String SELECTION_ATTRIBUTE = "modelSelection";
    
    public ModelFilteringModule() {
        super("ModelFilteringModule");
    }
    
    @Override
    public void setupModule(SetupContext context) {
        context.addBeanSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(
                    SerializationConfig config,
                    BeanDescription beanDesc,
                    List<BeanPropertyWriter> beanProperties) {
                
                // Si el bean no té anotacions de filtratge, deixar-lo intacte
                if (!hasFilteringAnnotations(beanDesc.getClass())) {
                    return beanProperties;
                }
                
                // Decorar tots els BeanPropertyWriter
                return beanProperties.stream()
                    .map(writer -> new FilteredBeanPropertyWriter(writer))
                    .toList();
            }
        });
    }
    
    private boolean hasFilteringAnnotations(Class<?> clazz) {
        // Comprovar si el bean té @ModelBasic o @ModelSections
    }
    
    private static class FilteredBeanPropertyWriter extends BeanPropertyWriter {
        // Implementar lògica de filtratge basat en ModelSelection
    }
}
```

**Responsabilitats:**
- Inspeccionar les propietats de cada bean
- Decorar els `BeanPropertyWriter` per aplicar el filtratge
- Deixar beans sense marques intactes (paginació, infraestructura)
- Aplicar el filtratge recursivament als objectes nestats

#### 4.2.4. ModelFilteringAdvice (ResponseBodyAdvice)

```java
@ControllerAdvice
public class ModelFilteringAdvice implements ResponseBodyAdvice<Object> {
    private static final String VARY_HEADER = "Vary";
    private static final String SELECTION_ATTRIBUTE = "modelSelection";
    
    @Override
    public boolean supports(MethodParameter returnType,
                          Class<? extends HttpMessageConverter<?>> converterType) {
        // Només aplicar a convertidors Jackson i endpoints amb @ModelFiltering
    }
    
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                 MediaType selectedContentType,
                                 Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                 ServerHttpRequest request, ServerHttpResponse response) {
        
        // 1. Obtenir anotació @ModelFiltering
        // 2. Llegir valor segons l'origen (query/header)
        // 3. Si no hi ha valor, retornar cos original
        // 4. Parsejar i validar (400 si invàlid)
        // 5. Afegir Vary header si és header
        // 6. Embolcallar el cos amb MappingJacksonValue + selecció
    }
}
```

**Responsabilitats:**
- Interceptar abans d'escriure el cos de la resposta
- Validar l'anotació `@ModelFiltering`
- Extreure el valor del paràmetre o header
- Validar el format del valor
- Afegir el `Vary` header
- Embolcallar el cos amb la selecció

#### 4.2.5. ModelFilteringConfiguration

```java
@Configuration
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "models.filtratge", havingValue = "true")
@ConditionalOnClass({HttpMessageConverter.class, ObjectMapper.class})
@AutoConfigureAfter(JacksonAutoConfiguration.class)
public class ModelFilteringConfiguration {
    
    @Bean
    public ModelFilteringAdvice modelFilteringAdvice() {
        return new ModelFilteringAdvice();
    }
    
    @Bean
    public WebMvcConfigurer modelFilteringWebMvcConfigurer(
            List<HttpMessageConverter<?>> converters,
            ObjectMapper objectMapper) {
        
        return new WebMvcConfigurer() {
            @Override
            public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
                // Crear un nou ObjectMapper amb el mòdul de filtratge
                ObjectMapper filteringObjectMapper = Jackson2ObjectMapperBuilder
                    .from(objectMapper)
                    .build()
                    .registerModule(new ModelFilteringModule());
                
                // Afegir un nou converter amb l'ObjectMapper personalitzat
                converters.add(0, new MappingJackson2HttpMessageConverter(filteringObjectMapper));
            }
        };
    }
}
```

**Responsabilitats:**
- Registrar el `ModelFilteringAdvice`
- Configurar el `WebMvcConfigurer` per afegir el converter personalitzat
- Condicionar la càrrega a `models.filtratge=true`

#### 4.2.6. Auto-configuració (Spring Boot 4.x)

**Fitxer:** `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
```
cat.albirar.dynmod.demo.filters.ModelFilteringConfiguration
```

### 4.3. Modificacions als Models Existents

**VehicleModelBean.java:**
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehicleModelBean {
    @ModelBasic
    private Optional<String> line;
    
    @ModelBasic
    private String model;
    
    @ModelBasic
    private VehicleType type;
    
    @ModelBasic
    private int inceptionYear;
    
    @ModelBasic
    private int construction;
    
    @ModelBasic
    private Optional<Instant> commercialization;
    
    @ModelBasic
    private Optional<Instant> retirement;
    
    @ModelSections(ModelPart.BRAND)
    private BrandBean brand;
    
    @ModelSections(ModelPart.MARKET)
    private Map<String, MarketDataBean> marketData;
    
    @ModelSections(ModelPart.PARTS)
    private Map<String, VehiclePartBean> parts;
}
```

**BrandBean.java:**
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class BrandBean {
    @ModelBasic
    private String manufacturerWMI;
    
    @ModelBasic
    private String commercialBrandName;
    
    @ModelBasic
    private String manufacturer;
    
    @ModelBasic
    private String country;
}
```

**MarketDataBean.java:**
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class MarketDataBean {
    @ModelBasic
    private String country;
    
    @ModelBasic
    private Instant startCommercializing;
    
    @ModelBasic
    private Instant endCommercializing;
    
    @ModelBasic
    private String commercialName;
    
    @ModelSections(ModelPart.ECONOMIC)
    private Currency currency;
    
    @ModelSections(ModelPart.ECONOMIC)
    private float localMarketPrice;
    
    @ModelSections(ModelPart.ECONOMIC)
    private float marketPrice;
    
    @ModelSections(ModelPart.ECONOMIC)
    private float margin;
}
```

**VehiclePartBean.java:**
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehiclePartBean {
    @ModelBasic
    private String partNumber;
    
    @ModelBasic
    private String name;
    
    @ModelBasic
    private String mountingReference;
    
    @ModelSections(ModelPart.ECONOMIC)
    private float marketPrice;
    
    @ModelSections(ModelPart.ECONOMIC)
    private float margin;
}
```

### 4.4. Modificacions als Controllers

**VehicleController.java:**
```java
@RestController
@RequestMapping("/vehicles")
@ModelFiltering  // <-- Afegir aquesta anotació
public class VehicleController {
    // ... existing code ...
}
```

**BrandController.java:**
```java
@RestController
@RequestMapping("/brands")
@ModelFiltering  // <-- Afegir aquesta anotació
public class BrandController {
    // ... existing code ...
}
```

### 4.5. Configuració de l'Aplicació

**application.properties:**
```properties
# Activar el filtratge
models.filtratge=true
```

---

## 5. Pla de Migració

### 5.1. Fases de la Migració

#### Fase 1: Preparació (1-2 dies)
- [ ] Crear nous paquets i classes (ModelPart, ModelBasic, ModelSections, etc.)
- [ ] Implementar ModelSelection
- [ ] Implementar ModelFilteringModule
- [ ] Implementar ModelFilteringAdvice
- [ ] Implementar ModelFilteringConfiguration
- [ ] Configurar auto-configuració

#### Fase 2: Adaptació (1-2 dies)
- [ ] Modificar tots els models per utilitzar les noves anotacions
- [ ] Modificar tots els controllers per afegir `@ModelFiltering`
- [ ] Actualitzar la configuració de l'aplicació

#### Fase 3: Eliminació de l'Antiga Implementació (1 dia)
- [ ] Eliminar `FilterInterceptor`
- [ ] Eliminar `FilteredViewPropertyFilter`
- [ ] Eliminar `FilterContext`
- [ ] Eliminar `EnableModelFiltering`
- [ ] Eliminar `FilterConfiguration`
- [ ] Eliminar `@JsonFilter` dels models
- [ ] Eliminar `FiltersModel` (reemplaçat per `ModelPart`)
- [ ] Eliminar `FilteredView` (reemplaçat per `ModelBasic` + `ModelSections`)

#### Fase 4: Tests (2-3 dies)
- [ ] Actualitzar tests existents per utilitzar el nou format de codis (`BPE` en lloc de `B,P,E`)
- [ ] Afegir tests per validar el nou comportament:
  - [ ] Validació de codis invàlids (400)
  - [ ] Vary header per headers
  - [ ] Filtratge en cascada
  - [ ] Comportament sense anotació
  - [ ] Comportament amb anotació a mètode
  - [ ] Comportament amb anotació a classe
  - [ ] Personalització de l'origen (query/header)
  - [ ] Personalització del nom

#### Fase 5: Validació (1-2 dies)
- [ ] Provar en entorn de desenvolupament
- [ ] Validar totes les funcionalitats existents
- [ ] Validar el nou comportament
- [ ] Corregir qualsevol problema

### 5.2. Canvis d'API

| **Aspecte** | **Actual** | **Nou** | **Impacte** |
|-------------|------------|---------|-------------|
| **Format codis** | `B,P,E` (separats per comes) | `BPE` (concatenats) | ⚠️ **Breaking change** |
| **Header** | `x-filter-model` | `X-Model-Sections` | ⚠️ **Breaking change** |
| **Validació** | Ignora codis invàlids | Retorna 400 | ✅ Millora |
| **Fallback** | Paràmetre > Header | No hi ha fallback | ⚠️ Canvi de comportament |

**Nota:** Els canvis d'API són breaking changes, però són necessaris per obtenir una implementació més robusta i estàndard.

### 5.3. Estratègia de Desplegament

1. **Desplegar en entorn de desenvolupament** i validar
2. **Actualitzar la documentació** de l'API
3. **Comunicar els canvis** als consumidors
4. **Desplegar en entorn de proves** i validar amb consumidors
5. **Desplegar en producció** en una finestra de manteniment

### 5.4. Rollback

En cas de problemes, es pot:
1. Revertir els canvis al codi
2. Desactivar la propietat `models.filtratge=true` per tornar a l'implementació antiga (si es manté)
3. Mantenir ambdues implementacions temporalment (no recomanat)

---

## 6. Conclusió i Recomanació

### 6.1. Resum de la Comparació

| **Criteri** | **Actual** | **Proposada** | **Guanyador** |
|-------------|------------|---------------|---------------|
| **Seguretat** | ❌ ThreadLocal | ✅ Sense estat mutable | **Proposada** |
| **Aïllament** | ❌ ObjectMapper global | ✅ ObjectMapper privat | **Proposada** |
| **Robustesa** | ❌ Validació laxista | ✅ Validació estricta | **Proposada** |
| **Estàndard** | ❌ No utilitza ResponseBodyAdvice | ✅ Utilitza ResponseBodyAdvice | **Proposada** |
| **Mantenibilitat** | ❌ Menys modular | ✅ Més modular | **Proposada** |
| **Flexibilitat** | ❌ Menys opcions | ✅ Més opcions | **Proposada** |
| **Senzillesa** | ✅ Més senzilla | ❌ Més complexa | **Actual** |
| **Compatibilitat** | ✅ Spring Boot 4.x | ✅ Spring Boot 4.x (amb ajusts) | **Empat** |

### 6.2. Recomanació Final

**✅ Implementar la nova arquitectura proposada**

La nova implementació és **superior en gairebé tots els aspectes** i resol els problemes de l'implementació actual:

1. **Elimina el ThreadLocal**: Evita problemes de memòria i concorrència
2. **Millor aïllament**: No afecta altres parts de l'aplicació
3. **Validació estricta**: Retorna 400 per errors (més professional)
4. **Més estàndard**: Utilitza les eines dissenyades per això (ResponseBodyAdvice)
5. **Millor per caches**: Implementa Vary header
6. **Més flexible**: Permet personalitzar l'origen i el nom
7. **Més mantenible**: Millor separació de responsabilitats

**El cost de la migració es compensarà amb una solució més professional, segura i mantenible.**

### 6.3. Alternatives Considerades

1. **Mantenir l'implementació actual**: No recomanat per els problemes de ThreadLocal i manca de robustesa
2. **Fusionar els millors elements**: Complex i podria resultar en una solució híbrida poc coherent
3. **Implementar la nova arquitectura**: **Recomanat** - Solució més professional i sostenible

### 6.4. Pròxims Passos

1. **Revisar aquesta proposta** i aprovar-la
2. **Prioritzar la migració** en el backlog
3. **Assignar recursos** per a la implementació
4. **Planificar la migració** segons el pla proposat
5. **Implementar i validar** en entorn de desenvolupament

---

## Apèndix A: Comparativa de Codi

### A.1. Exemple de Filtratge Actual

**Request:** `GET /vehicles?m=B,P`
**Response:**
```json
{
  "content": [
    {
      "brand": {"manufacturerWMI": "TOY", "commercialBrandName": "Toyota"},
      "model": "Corolla",
      "type": "Sedan",
      "inceptionYear": 1966
      // Sense marketData ni parts
    }
  ]
}
```

### A.2. Exemple de Filtratge Proposat

**Request:** `GET /vehicles?m=BP`
**Response:**
```json
{
  "content": [
    {
      "brand": {"manufacturerWMI": "TOY", "commercialBrandName": "Toyota"},
      "model": "Corolla",
      "type": "Sedan",
      "inceptionYear": 1966,
      "parts": {"TRD-001": {"partNumber": "TRD-001", "name": "Engine Oil Filter"}}
      // Sense marketData
    }
  ]
}
```

### A.3. Exemple d'Error Actual

**Request:** `GET /vehicles?m=B,X,INVALID`
**Response:** Retorna el model complet (ignora codis invàlids)

### A.4. Exemple d'Error Proposat

**Request:** `GET /vehicles?m=BX`
**Response:** HTTP 400 Bad Request amb missatge: "Invalid model part code: 'X' in input: BX"

---

## Apèndix B: Glossari

| **Term** | **Definició** |
|----------|---------------|
| **ResponseBodyAdvice** | Interceptor de Spring que permet modificar el cos de la resposta abans d'escriure'l |
| **BeanSerializerModifier** | Classe de Jackson que permet modificar com es serialitzen els beans |
| **MappingJacksonValue** | Contenidor de Spring que permet personalitzar la serialització JSON |
| **ThreadLocal** | Variable que manté un valor per fil d'execució (problemàtic en entorns asíncrons) |
| **Vary Header** | Header HTTP que indica al client quins headers afecten la cache |
| **ModelBasic** | Anotació que marca un camp com a sempre inclus en respostes filtrades |
| **ModelSections** | Anotació que associa un camp a una o més seccions opcionals |
| **ModelFiltering** | Anotació que habilita el filtratge per a un endpoint |

---

## Apèndix C: Referències

- [Document d'Arquitectura Original](./arquitectura-models-dinamics.md)
- [Spring Boot 4.x Documentation](https://docs.spring.io/spring-boot/docs/4.1.1/reference/html/)
- [Jackson Databind Documentation](https://github.com/FasterXML/jackson-databind)
- [Spring Framework ResponseBodyAdvice](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/bind/annotation/ResponseBodyAdvice.html)
