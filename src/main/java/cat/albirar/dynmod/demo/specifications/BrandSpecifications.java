package cat.albirar.dynmod.demo.specifications;

import org.springframework.data.jpa.domain.Specification;

import cat.albirar.dynmod.demo.entity.BrandEntity;

public class BrandSpecifications {

    public static Specification<BrandEntity> byManufacturer(String manufacturer) {
        return (root, query, cb) -> {
            if (manufacturer == null || manufacturer.isBlank()) {
                return null;
            }
            return cb.equal(root.get("manufacturer"), manufacturer);
        };
    }

    public static Specification<BrandEntity> byCountry(String country) {
        return (root, query, cb) -> {
            if (country == null || country.isBlank()) {
                return null;
            }
            return cb.equal(root.get("country"), country);
        };
    }

    public static Specification<BrandEntity> byCommercialBrandNamePattern(String pattern) {
        return (root, query, cb) -> {
            if (pattern == null || pattern.isBlank()) {
                return null;
            }
            return cb.like(
                cb.lower(root.get("commercialBrandName")),
                "%" + pattern.toLowerCase() + "%"
            );
        };
    }
}
