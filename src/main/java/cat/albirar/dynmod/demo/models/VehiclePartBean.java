package cat.albirar.dynmod.demo.models;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.demo.models.filters.FilteredView;
import cat.albirar.dynmod.demo.models.filters.FiltersModel;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
@JsonFilter("vehiclePartFilter")
public class VehiclePartBean {
    private String partNumber;
    private String name;
    private String mountingReference;
    @FilteredView(filters = {FiltersModel.Economic})
    private float marketPrice;
    @FilteredView(filters = {FiltersModel.Economic})
    private float margin;
}
