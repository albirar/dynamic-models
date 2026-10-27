package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Currency;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.filtering.model.ModelBasic;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSections;
import lombok.Data;

/**
 * Details about market data.
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class MarketDataBean {
    @ModelBasic
    private String country;
    @ModelBasic
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant startCommercializing;
    @ModelBasic
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant endCommercializing;
    @ModelBasic
    private String commercialName;
    @ModelSections(ModelPart.ECONOMIC)
    private Currency currency;
    @ModelSections(ModelPart.ECONOMIC)
    private float localMarketPrice;
    @ModelSections(ModelPart.ECONOMIC)
    private float marketPrice;
    @ModelSections(ModelPart.ECONOMIC)
    private float margin;
}