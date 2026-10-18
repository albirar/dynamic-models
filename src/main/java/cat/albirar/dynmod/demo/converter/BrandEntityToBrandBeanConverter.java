package cat.albirar.dynmod.demo.converter;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.models.BrandBean;

@Component
public class BrandEntityToBrandBeanConverter implements Converter<BrandEntity, BrandBean> {
    @Override
    public BrandBean convert(BrandEntity source) {
        if (source == null) {
            return new BrandBean();
        }
        BrandBean target = new BrandBean();
        target.setManufacturerWMI(source.getManufacturerWMI());
        target.setCommercialBrandName(source.getCommercialBrandName());
        target.setManufacturer(source.getManufacturer());
        target.setCountry(source.getCountry());
        return target;
    }
}
