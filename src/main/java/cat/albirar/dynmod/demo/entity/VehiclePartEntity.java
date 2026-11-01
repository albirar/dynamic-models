package cat.albirar.dynmod.demo.entity;

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
@Table(name = "vehicle_parts")
public class VehiclePartEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private VehicleEntity vehicle;

    @Column(name = "part_number", length = 50, nullable = false)
    private String partNumber;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "mounting_reference", length = 100)
    private String mountingReference;

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
        return other instanceof VehiclePartEntity entity && id != null && id.equals(entity.getId());
    }

    @Override
    public int hashCode() {
        return VehiclePartEntity.class.hashCode();
    }
}
