package cat.albirar.dynmod.demo.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.models.BrandBean;

class BrandEntityToBrandBeanConverterTest {

    private BrandEntityToBrandBeanConverter converter;

    @BeforeEach
    void setUp() {
        converter = new BrandEntityToBrandBeanConverter();
    }

    @Test
    void convert_ShouldMapAllFieldsCorrectly() {
        // Given
        BrandEntity entity = new BrandEntity();
        entity.setManufacturerWMI("TOY");
        entity.setCommercialBrandName("Toyota");
        entity.setManufacturer("Toyota Motor Corporation");
        entity.setCountry("US");

        // When
        BrandBean result = converter.convert(entity);

        // Then
        assertNotNull(result);
        assertEquals("TOY", result.getManufacturerWMI());
        assertEquals("Toyota", result.getCommercialBrandName());
        assertEquals("Toyota Motor Corporation", result.getManufacturer());
        assertEquals("US", result.getCountry());
    }

    @Test
    void convert_ShouldHandleNullInput() {
        // When
        BrandBean result = converter.convert(null);

        // Then
        assertNotNull(result);
    }
}
