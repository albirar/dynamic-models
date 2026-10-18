package cat.albirar.dynmod.demo.models;

import java.time.Instant;
import java.util.Currency;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import cat.albirar.dynmod.demo.models.filters.FilteredView;
import cat.albirar.dynmod.demo.models.filters.FiltersModel;
import lombok.Data;

/**
 * Details about market data.
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(value = Include.NON_ABSENT)
@JsonFilter("marketDataFilter")
public class MarketDataBean {
    private String country;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant startCommercializing;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant endCommercializing;
    private String commercialName;
    @FilteredView(filters = {FiltersModel.Economic})
    private Currency currency;
    @FilteredView(filters = {FiltersModel.Economic})
    private float localMarketPrice;
    @FilteredView(filters = {FiltersModel.Economic})
    private float marketPrice;
    @FilteredView(filters = {FiltersModel.Economic})
    private float margin;
}