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
import lombok.Data;

@Data
@Entity
@Table(name = "vehicle_parts")
public class VehiclePartEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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
}
