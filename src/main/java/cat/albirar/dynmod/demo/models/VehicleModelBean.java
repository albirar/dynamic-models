package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehicleModelBean {
    private BrandBean brand;
    /**
     * If this model belongs to a long line (like 'mustang'), this is the line model.
     */
    private Optional<String> line;
    /**
     * Model name.
     */
    private String model;
    /**
     * Vehicle type
     */
    private VehicleType type;
    /**
     * Model year inception
     */
    private int inceptionYear;
    /**
     * Construction year
     */
    private int construction;
    /**
     * Commercialization date.
     */
    private Optional<Instant> commercialization;
    /**
     * Retirement date.
     */
    private Optional<Instant> retirement;
    private Map<String, MarketDataBean> marketData;
    private Map<String, VehiclePartBean> parts;
}
