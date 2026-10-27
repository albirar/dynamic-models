# Modelos dinámicos en API REST

[Català](README.md) · [English](README.en.md) · **Español**

Prueba de concepto de una API REST en la que **el cliente decide qué secciones del modelo quiere recibir**, con códigos compactos enviados como query parameter o header:

```http
GET /vehicles?m=BP              → datos básicos + marca (B) + piezas (P)
GET /vehicles                   → modelo completo, como cualquier API
X-Model-Sections: M             → datos básicos + datos de mercado, vía header
```

El servicio mantiene **un único modelo de respuesta** por recurso. La selección se aplica durante la serialización JSON, sin duplicar DTO, endpoints ni consultas.

Stack: Java 21, Spring Boot 4.1 (Spring Framework 7, Jackson 3), Spring Data JPA, H2.

---

## Por qué

La mayoría de API REST devuelven siempre el modelo entero. Es sencillo, pero a medida que los modelos crecen aparecen problemas conocidos:

- **Over-fetching**: una pantalla de listado que solo muestra nombre y tipo recibe también precios, mercados y piezas de cada elemento.
- **Proliferación de endpoints o DTO**: para evitarlo se crean variantes (`/vehicles/summary`, `VehicleLightDto`, `VehicleDetailDto`...) que hay que mantener, documentar y versionar por separado.
- **Coste de red y de parsing**: relevante en clientes móviles, en tráfico entre centros de datos y en APIs públicas con coste de salida.

Este proyecto propone un punto intermedio entre "siempre todo" y un lenguaje de consulta completo como GraphQL: el modelo se divide en **secciones con nombre** que el cliente puede pedir u omitir.

## Cómo funciona

### 1. Marcar el modelo

```java
public class VehicleModelBean {
    @ModelBasic                              // siempre incluido
    private String model;

    @ModelSections(ModelPart.BRAND)          // incluido si se pide B
    private BrandBean brand;

    @ModelSections(ModelPart.MARKET)         // incluido si se pide M
    private Map<String, MarketDataBean> marketData;
}

public class MarketDataBean {
    @ModelBasic
    private String country;

    @ModelSections(ModelPart.ECONOMIC)       // precios solo si se pide E
    private float marketPrice;
}
```

### 2. Activarlo en el endpoint

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

### 3. Pedir secciones desde el cliente

| Código | Sección | Contenido |
| --- | --- | --- |
| `B` | `BRAND` | Marca del vehículo |
| `M` | `MARKET` | Datos de comercialización por país |
| `P` | `PARTS` | Piezas del vehículo |
| `E` | `ECONOMIC` | Precios y márgenes (dentro de mercados y piezas) |

| Petición | Resultado |
| --- | --- |
| Sin `m` ni header | Modelo completo (comportamiento habitual) |
| `?m=` (vacío) | Solo datos básicos |
| `?m=BP` | Básicos + marca + piezas, sin precios |
| `?m=PE` | Básicos + piezas con precios |
| `X-Model-Sections: BP` | Igual que `?m=BP` |
| `?m=M` + `X-Model-Sections: B` | El query parameter tiene preferencia: solo `M` |
| `?m=BX`, `?m=b`, `?m=B,P`, `?m=B&m=P` | HTTP 400 |

Reglas:

- Los códigos van **concatenados**, distinguen mayúsculas y los repetidos se ignoran (`BBP` = `BP`).
- La selección **se aplica en cascada**: `E` afecta a los precios de mercados y de piezas, pero solo si `M` o `P` están presentes. Un hijo no puede hacer aparecer un padre excluido.
- Si el bean tiene alguna marca, las propiedades **sin marca se omiten** cuando hay selección. Un bean sin ninguna marca (por ejemplo `BrandBean` o los envoltorios de paginación) se serializa siempre completo.
- Header y query parameter se pueden usar indistintamente. **Solo cuando están ambos**, manda el query parameter y el header se ignora sin validarlo. Esto permite que un cliente fije una selección por defecto en el header y la sustituya en peticiones concretas.
- Los endpoints filtrados responden siempre con `Vary: X-Model-Sections`, para que las cachés HTTP no mezclen respuestas.
- Las reglas habituales de Jackson (`@JsonInclude`, `@JsonIgnore`, `@JsonFormat`, vistas, filtros propios) siguen vigentes.

### Ejemplo real

Con los 25 vehículos de la demo (`GET /vehicles?size=100`):

| Selección | Tamaño de la respuesta |
| --- | --- |
| Modelo completo | 7.817 bytes |
| `m=B` | 6.156 bytes (−21 %) |
| `m=` | 3.234 bytes (−59 %) |

En modelos reales, con colecciones grandes (historiales, líneas, documentos), la diferencia suele ser mucho mayor.

---

## Aplicación en una API REST en producción

### Casos en los que aporta valor

- **Listados y detalles con el mismo recurso.** Un listado pide `m=` y la vista de detalle pide `m=BMP`, sin mantener dos DTO ni dos endpoints.
- **Clientes heterogéneos.** La app móvil, la web y las integraciones de terceros consumen el mismo endpoint con selecciones diferentes.
- **Datos sensibles al coste de transferencia.** Redes móviles, salida de nube facturada, tráfico entre regiones.
- **Backends for Frontends y gateways** que agregan varias API y solo necesitan una parte de cada respuesta.
- **Evolución del modelo sin romper clientes.** Añadir una sección nueva no cambia la respuesta de los clientes que ya envían una selección. Los que no la envían recibirán el campo nuevo, como en cualquier API.

### Cómo desplegarlo

1. **Opt-in gradual.** La infraestructura se activa con `models.filtratge=true` y cada endpoint con `@ModelFiltering`. Sin selección la respuesta es idéntica a la anterior, de modo que los clientes existentes no lo notan.
2. **Publicar el contrato.** La anotación no genera OpenAPI por sí sola. Hay que documentar, por endpoint, el query parameter, el header y las secciones disponibles con sus códigos. Los códigos forman parte del contrato: no se pueden reutilizar ni cambiar de significado.
3. **Cachés y CDN.** El query parameter normalmente ya forma parte de la clave de caché. Para el header, hay que comprobar que el CDN o el proxy respetan `Vary` (o incluir el header en la clave de caché de forma explícita).
4. **CORS.** Si un navegador tiene que enviar `X-Model-Sections`, hay que añadirlo a `Access-Control-Allow-Headers`.
5. **Observabilidad.** Monitorizar los errores 400 por selección inválida (clientes mal configurados) y el tamaño de las respuestas por selección, para confirmar el beneficio.
6. **Pruebas de humo después de cada despliegue.** `scripts/smoke-test.sh <URL>` comprueba que la API responde y que el filtrado está activo.

### Qué no es

- **No es un mecanismo de autorización.** Sin selección se devuelve el modelo completo; todo lo que el modelo puede contener debe estar ya autorizado para el usuario. Para ocultar datos sensibles hay que filtrarlos antes, en la capa de servicio.
- **No es una proyección de consulta.** El filtrado se hace al serializar: la base de datos y el mapeo a DTO hacen el mismo trabajo con o sin selección.

---

## Ventajas e inconvenientes frente a devolver siempre el modelo completo

### Ventajas

| | |
| --- | --- |
| **Menos datos transferidos** | Los clientes reciben solo lo que usan. Menos latencia, menos coste de red y menos parsing en el cliente. |
| **Un solo modelo y un solo endpoint** | Evita la proliferación de DTO "light", "summary" y "detail" y de endpoints paralelos que hay que mantener sincronizados. |
| **Compatible con los clientes existentes** | Sin selección el comportamiento es exactamente el de antes. Se puede adoptar endpoint a endpoint y cliente a cliente. |
| **Contrato simple** | Códigos de una letra, fáciles de enviar desde cualquier cliente HTTP, sin lenguaje de consulta ni biblioteca de cliente especial. |
| **Secciones estables y con sentido de negocio** | El cliente pide "datos de mercado", no una lista de campos concretos. El servidor puede reorganizar campos dentro de una sección sin romper el contrato. |
| **Compatible con cachés HTTP** | Las respuestas son GET normales con `Vary`; no hace falta cambiar de infraestructura como pasaría con GraphQL vía POST. |
| **Validación estricta** | Un código desconocido es un 400 explícito, no una respuesta silenciosamente incompleta. |

### Inconvenientes

| | |
| --- | --- |
| **No ahorra trabajo en el servidor** | Los datos se cargan y se mapean igualmente. Si el coste principal es la base de datos o un servicio remoto, hay que combinarlo con proyecciones o carga lazy por sección. |
| **Contrato más complejo** | Cada campo del modelo puede estar presente o no según la petición. Los clientes tipados deben tratar como opcionales todos los campos que no son básicos, y la documentación debe explicar las secciones. |
| **OpenAPI no lo expresa** | Las herramientas de generación de clientes no saben qué campos dependen de cada sección; hay que documentarlo a mano o con extensiones propias. |
| **Fragmentación de caché** | Cada combinación de secciones es una entrada diferente. Con 4 secciones hay hasta 16 variantes por URL. |
| **Granularidad limitada** | Las secciones son de grano grueso: no se puede pedir un campo suelto. La misma selección se aplica a todos los niveles del modelo. |
| **Espacio de códigos finito** | Una letra por sección. Basta para decenas de secciones, pero hay que gobernarlas y no se pueden reciclar. |
| **Requiere disciplina en los DTO** | Al marcar un bean, todo campo nuevo sin marca desaparece de las respuestas filtradas. Los tests de contrato deben proteger contra esta regresión. |
| **Más combinaciones que probar** | El comportamiento depende de la selección; las pruebas deben cubrir como mínimo el modelo completo, el básico y cada sección. |

### Frente a otras alternativas

| Alternativa | Comparación |
| --- | --- |
| **Modelo completo siempre** | Más simple, más previsible y mejor soportado por las herramientas; aceptable mientras los modelos son pequeños o los clientes son homogéneos. |
| **Endpoints o DTO específicos** | Contrato explícito y tipado, pero multiplica código y documentación con cada nueva necesidad de cliente. |
| **Sparse fieldsets** (`fields=a,b` en JSON:API o las respuestas parciales de Google) | Granularidad por campo, más flexible pero más verboso, más difícil de cachear y acopla el cliente a los nombres internos de los campos. |
| **GraphQL** | Selección arbitraria y tipada, y resuelve también la carga de datos. Requiere otro stack, otro modelo de caché y otro contrato. |

**Cuándo usarlo:** modelos con bloques de datos claramente separables, clientes con necesidades diferentes sobre el mismo recurso y una API REST que se quiere mantener. **Cuándo no:** modelos pequeños, un único cliente, o cuando el coste real está en la base de datos y no en la respuesta.

---

## Arquitectura

```
client ─HTTP─► Spring MVC ─► controller ─► servicio ─► JPA
                   │
                   ▼
         ModelFilteringAdvice        lee ?m o X-Model-Sections, valida (400), añade Vary
                   │                 y pasa la selección como hint de escritura
                   ▼
  JacksonJsonHttpMessageConverter    copia privada del JsonMapper de Boot + ModelFilteringModule
                   │
                   ▼
   PropertyFilter por respuesta      incluye @ModelBasic y las @ModelSections seleccionadas,
                                     en cascada por todo el grafo
```

- **Sin estado compartido**: la selección es un objeto inmutable por respuesta; no hay `ThreadLocal`.
- **Aislado**: solo el converter JSON de servidor usa el mapper con el módulo; el `JsonMapper` de la aplicación y los clientes HTTP no cambian.
- **Infraestructura como biblioteca**: el paquete `cat.albirar.dynmod.filtering` no depende de la aplicación y se carga por auto-configuración.

Detalles en:

- [Arquitectura de filtrado de modelos](src/main/docs/arquitectura-models-dinamics.es.md): contrato y diseño.
- [Nueva implementación](src/main/docs/nova-implementacio.es.md): adaptación a Spring Boot 4 / Jackson 3 y migración desde la versión anterior.

### Estructura

```
src/main/java/cat/albirar/dynmod/
├── filtering/                    infraestructura reutilizable
│   ├── model/                    ModelPart, @ModelBasic, @ModelSections, ModelSelection
│   ├── jackson/                  módulo, introspector, filtro y proveedor de filtros
│   └── web/                      @ModelFiltering, advice, auto-configuración
└── demo/                         aplicación de ejemplo (vehículos y marcas)
scripts/smoke-test.sh             pruebas de humo contra una instancia desplegada
```

---

## Ejecución

Requisitos: JDK 21 y Maven.

```bash
mvn spring-boot:run
```

| URL | |
| --- | --- |
| `http://localhost:8080/vehicles?m=BP` | Vehículos con marca y piezas |
| `http://localhost:8080/brands` | Marcas |
| `http://localhost:8080/swagger-ui.html` | Documentación OpenAPI |

Parámetros de búsqueda de `/vehicles`: `brandManufacturerWMI`, `country`, `type`, `commercialNamePattern`, y paginación estándar (`page`, `size`, `sort`).

## Pruebas

```bash
mvn test                        # todas: unitarias, integración, E2E y humo
mvn test -Dgroups=e2e           # E2E sobre un servidor HTTP real
mvn test -Dgroups=smoke         # pruebas de humo
scripts/smoke-test.sh http://localhost:8080   # humo contra una instancia en marcha
```

| Nivel | Qué cubre |
| --- | --- |
| Unitarias | Parser de selecciones; módulo Jackson (marcas, cascada, configuraciones incorrectas, concurrencia) |
| Integración | Condiciones de auto-configuración; contexto Boot real con `MockMvc` (formas de respuesta, header y query parameter, preferencia, `Vary`, errores, herencia de anotaciones) |
| E2E | Servidor HTTP real con los datos de `data.sql`: secciones y cascada, todas las secciones equivalen al modelo completo, header equivalente al query parameter, preferencia, errores 400, búsqueda y paginación, peticiones concurrentes |
| Humo | La aplicación arranca, los endpoints principales responden, el filtrado está activo y el OpenAPI se publica |
