package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Currency;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.Data;

/**
 * Details about market data.
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
public class MarketDataBean {
    private String country;
    private Instant startCommercializing;
    private Instant endCommercializing;
    private String commercialName;
    private Currency currency;
    private float localMarketPrice;
    private float marketPrice;
    private float margin;
}