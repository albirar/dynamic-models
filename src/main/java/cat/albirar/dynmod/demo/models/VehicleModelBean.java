package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class VehicleModelBean {
    @ModelSections(ModelPart.BRAND)
    private BrandBean brand;
    /**
     * If this model belongs to a long line (like 'mustang'), this is the line model.
     */
    @ModelBasic
    private Optional<String> line;
    /**
     * Model name.
     */
    @ModelBasic
    private String model;
    /**
     * Vehicle type
     */
    @ModelBasic
    private VehicleType type;
    /**
     * Model year inception
     */
    @ModelBasic
    private int inceptionYear;
    /**
     * Construction year
     */
    @ModelBasic
    private int construction;
    /**
     * Commercialization date.
     */
    @ModelBasic
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Optional<Instant> commercialization;
    /**
     * Retirement date.
     */
    @ModelBasic
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Optional<Instant> retirement;
    @ModelSections(ModelPart.MARKET)
    private Map<String, MarketDataBean> marketData;
    @ModelSections(ModelPart.PARTS)
    private Map<String, VehiclePartBean> parts;
}
