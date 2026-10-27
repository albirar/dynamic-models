package cat.albirar.dynmod.demo.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSections;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehiclePartBean {
    @ModelBasic
    private String partNumber;
    @ModelBasic
    private String name;
    @ModelBasic
    private String mountingReference;
    @ModelSections(ModelPart.ECONOMIC)
    private float marketPrice;
    @ModelSections(ModelPart.ECONOMIC)
    private float margin;
}
