package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class VehicleModelBean {
    private String brandId;
    /**
     * If this model belongs to a long line (like 'mustang'), this is the line model.
     */
    private String line;
    /**
     * Model name.
     */
    private String model;
    private VehicleType type;
    private Instant inception;
    private Instant construction;
    private Instant comercialization;
    private Instant retirement;
    private Map<Locale.IsoCountryCode, MarketDataBean> marketData;
    private Map<String, VehiclePartBean> parts;
}
