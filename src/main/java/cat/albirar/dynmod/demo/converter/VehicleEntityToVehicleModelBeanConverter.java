package cat.albirar.dynmod.demo.converter;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleMarketDataEntity;
import cat.albirar.dynmod.demo.entity.VehiclePartEntity;
import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.models.MarketDataBean;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.models.VehiclePartBean;
import cat.albirar.dynmod.demo.models.VehicleType;

@Component
public class VehicleEntityToVehicleModelBeanConverter implements Converter<VehicleEntity, VehicleModelBean> {

    private final BrandEntityToBrandBeanConverter brandConverter;

    public VehicleEntityToVehicleModelBeanConverter(BrandEntityToBrandBeanConverter brandConverter) {
        this.brandConverter = brandConverter;
    }

    @Override
    public VehicleModelBean convert(VehicleEntity source) {
        if (source == null) {
            return new VehicleModelBean();
        }
        VehicleModelBean target = new VehicleModelBean();
        
        // Brand
        if (source.getBrand() != null) {
            BrandBean brandBean = brandConverter.convert(source.getBrand());
            target.setBrand(brandBean);
        }
        
        // Line
        target.setLine(Optional.ofNullable(source.getLine()));
        
        // Model
        target.setModel(source.getModel());
        
        // Type
        if (source.getType() != null) {
            VehicleType type = VehicleType.valueOf(source.getType().name());
            target.setType(type);
        }
        
        // Years
        target.setInceptionYear(source.getInceptionYear());
        target.setConstruction(source.getConstructionYear());
        
        // Dates
        target.setCommercialization(Optional.ofNullable(source.getCommercialization()));
        target.setRetirement(Optional.ofNullable(source.getRetirement()));
        
        // Market Data
        if (source.getMarketData() != null && !source.getMarketData().isEmpty()) {
            Map<String, MarketDataBean> marketDataMap = source.getMarketData().stream()
                .collect(Collectors.toMap(
                    VehicleMarketDataEntity::getCountry,
                    this::convertMarketData
                ));
            target.setMarketData(marketDataMap);
        }
        
        // Parts
        if (source.getParts() != null && !source.getParts().isEmpty()) {
            Map<String, VehiclePartBean> partsMap = source.getParts().stream()
                .collect(Collectors.toMap(
                    VehiclePartEntity::getPartNumber,
                    this::convertPart
                ));
            target.setParts(partsMap);
        }
        
        return target;
    }

    private MarketDataBean convertMarketData(VehicleMarketDataEntity source) {
        MarketDataBean target = new MarketDataBean();
        target.setCountry(source.getCountry());
        target.setStartCommercializing(source.getStartCommercializing());
        target.setEndCommercializing(source.getEndCommercializing());
        target.setCommercialName(source.getCommercialName());
        target.setCurrency(java.util.Currency.getInstance(source.getCurrency()));
        target.setLocalMarketPrice(source.getLocalMarketPrice());
        target.setMarketPrice(source.getMarketPrice());
        target.setMargin(source.getMargin());
        return target;
    }

    private VehiclePartBean convertPart(VehiclePartEntity source) {
        VehiclePartBean target = new VehiclePartBean();
        target.setPartNumber(source.getPartNumber());
        target.setName(source.getName());
        target.setMountingReference(source.getMountingReference());
        target.setMarketPrice(source.getMarketPrice());
        target.setMargin(source.getMargin());
        return target;
    }
}
