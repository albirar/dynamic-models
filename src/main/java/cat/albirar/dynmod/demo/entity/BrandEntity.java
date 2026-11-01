package cat.albirar.dynmod.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Entity
@Table(name = "brands")
public class BrandEntity {
    @Id
    @Column(name = "manufacturer_wmi", length = 10, nullable = false)
    private String manufacturerWMI;

    @Column(name = "commercial_brand_name", length = 100, nullable = false)
    private String commercialBrandName;

    @Column(name = "manufacturer", length = 100, nullable = false)
    private String manufacturer;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    /**
     * Identity is the manufacturerWMI: comparing or hashing never touches lazy associations. The hash
     * code is constant so it does not change when the identifier is assigned.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof BrandEntity entity && manufacturerWMI != null && manufacturerWMI.equals(entity.getManufacturerWMI());
    }

    @Override
    public int hashCode() {
        return BrandEntity.class.hashCode();
    }
}
