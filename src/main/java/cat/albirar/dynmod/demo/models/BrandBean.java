package cat.albirar.dynmod.demo.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
@JsonFilter("brandFilter")
public class BrandBean {
    private String manufacturerWMI;
    private String commercialBrandName;
    /**
     * Nom 'legal' del fabricant o de la casa matriu a la qual pertany aquesta marca
     */
    private String manufacturer;
    /**
     * País de la marca, encara que no coincideixi amb el manufacturer (ISO 3166-1 alpha-2 code)
     */
    private String country;
}
