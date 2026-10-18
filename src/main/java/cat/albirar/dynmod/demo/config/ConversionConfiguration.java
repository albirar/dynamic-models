package cat.albirar.dynmod.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import cat.albirar.dynmod.demo.converter.BrandEntityToBrandBeanConverter;
import cat.albirar.dynmod.demo.converter.VehicleEntityToVehicleModelBeanConverter;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class ConversionConfiguration {

    private final BrandEntityToBrandBeanConverter brandEntityToBrandBeanConverter;
    private final VehicleEntityToVehicleModelBeanConverter vehicleEntityToVehicleModelBeanConverter;

    @Bean
    ConversionService conversionService() {
        DefaultConversionService conversionService = new DefaultConversionService();
        conversionService.addConverter(brandEntityToBrandBeanConverter);
        conversionService.addConverter(vehicleEntityToVehicleModelBeanConverter);
        return conversionService;
    }
}
