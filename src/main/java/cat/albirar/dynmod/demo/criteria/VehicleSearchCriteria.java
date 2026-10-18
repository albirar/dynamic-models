package cat.albirar.dynmod.demo.criteria;

import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import lombok.Data;

@Data
public class VehicleSearchCriteria {
    private String brandManufacturerWMI;
    private String country;
    private VehicleTypeEntity type;
    private String commercialNamePattern;
}
