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
    private String brand;
    private String brandLine;
    private String model;
    private VehicleType type;
    private Instant inception;
    private Instant construction;
    private Instant comercialization;
    private Instant retirement;
    private Map<Locale.IsoCountryCode, MarketDataBean> marketData;
    private Map<String, VehiclePartBean> parts;
}
