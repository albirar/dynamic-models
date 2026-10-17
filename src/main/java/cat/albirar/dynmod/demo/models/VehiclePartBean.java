package cat.albirar.dynmod.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehiclePartBean {
    private String partNumber;
    private String name;
    private String mountingReference;
    private float marketPrice;
    private float margin;
}
