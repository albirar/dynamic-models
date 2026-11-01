package cat.albirar.dynmod.demo.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Entity
@Table(name = "vehicles")
public class VehicleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_wmi", referencedColumnName = "manufacturer_wmi", nullable = false)
    private BrandEntity brand;

    @Column(name = "line", length = 100)
    private String line;

    @Column(name = "model", length = 100, nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private VehicleTypeEntity type;

    @Column(name = "inception_year")
    private int inceptionYear;

    @Column(name = "construction_year")
    private int constructionYear;

    @Column(name = "commercialization")
    private Instant commercialization;

    @Column(name = "retirement")
    private Instant retirement;

    @ToString.Exclude
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehicleMarketDataEntity> marketData = new ArrayList<>();

    @ToString.Exclude
    @OneToMany(mappedBy = "vehicle", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VehiclePartEntity> parts = new ArrayList<>();

    /**
     * Identity is the id: comparing or hashing never touches lazy associations. The hash
     * code is constant so it does not change when the identifier is assigned.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof VehicleEntity entity && id != null && id.equals(entity.getId());
    }

    @Override
    public int hashCode() {
        return VehicleEntity.class.hashCode();
    }
}
