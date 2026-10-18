package cat.albirar.dynmod.demo.specifications;

import org.springframework.data.jpa.domain.Specification;

import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import jakarta.persistence.criteria.Join;

public class VehicleSpecifications {

    public static Specification<VehicleEntity> byBrandManufacturerWMI(String wmi) {
        return (root, query, cb) -> {
            if (wmi == null) {
                return null;
            }
            Join<VehicleEntity, ?> brandJoin = root.join("brand");
            return cb.equal(brandJoin.get("manufacturerWMI"), wmi);
        };
    }

    public static Specification<VehicleEntity> byBrandCountry(String country) {
        return (root, query, cb) -> {
            if (country == null) {
                return null;
            }
            Join<VehicleEntity, ?> brandJoin = root.join("brand");
            return cb.equal(brandJoin.get("country"), country);
        };
    }

    public static Specification<VehicleEntity> byType(VehicleTypeEntity type) {
        return (root, query, cb) -> {
            if (type == null) {
                return null;
            }
            return cb.equal(root.get("type"), type);
        };
    }

    public static Specification<VehicleEntity> byCommercialNamePattern(String pattern) {
        return (root, query, cb) -> {
            if (pattern == null || pattern.isBlank()) {
                return null;
            }
            Join<VehicleEntity, ?> marketDataJoin = root.join("marketData");
            return cb.like(
                cb.lower(marketDataJoin.get("commercialName")),
                "%" + pattern.toLowerCase() + "%"
            );
        };
    }
}
