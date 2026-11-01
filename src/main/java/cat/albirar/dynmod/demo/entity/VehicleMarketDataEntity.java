package cat.albirar.dynmod.demo.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Entity
@Table(name = "vehicle_market_data")
public class VehicleMarketDataEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @Column(name = "country", length = 2, nullable = false)
    private String country;

    @Column(name = "start_commercializing")
    private Instant startCommercializing;

    @Column(name = "end_commercializing")
    private Instant endCommercializing;

    @Column(name = "commercial_name", length = 100)
    private String commercialName;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "local_market_price")
    private float localMarketPrice;

    @Column(name = "market_price")
    private float marketPrice;

    @Column(name = "margin")
    private float margin;

    /**
     * Identity is the id: comparing or hashing never touches lazy associations. The hash
     * code is constant so it does not change when the identifier is assigned.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof VehicleMarketDataEntity entity && id != null && id.equals(entity.getId());
    }

    @Override
    public int hashCode() {
        return VehicleMarketDataEntity.class.hashCode();
    }
}
