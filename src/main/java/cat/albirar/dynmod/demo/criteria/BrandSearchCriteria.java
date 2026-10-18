package cat.albirar.dynmod.demo.criteria;

import lombok.Data;

@Data
public class BrandSearchCriteria {
    private String manufacturer;
    private String country;
    private String commercialBrandNamePattern;
}
