package cat.albirar.dynmod.demo.entity;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
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
}
