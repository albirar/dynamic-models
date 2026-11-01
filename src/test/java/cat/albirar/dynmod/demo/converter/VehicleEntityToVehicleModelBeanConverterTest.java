package cat.albirar.dynmod.demo.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleMarketDataEntity;
import cat.albirar.dynmod.demo.entity.VehiclePartEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.MarketDataBean;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.models.VehiclePartBean;
import cat.albirar.dynmod.demo.models.VehicleType;
import cat.albirar.dynmod.filtering.model.ModelSelection;

class VehicleEntityToVehicleModelBeanConverterTest {

    private VehicleEntityToVehicleModelBeanConverter converter;
    private BrandEntityToBrandBeanConverter brandConverter;

    @BeforeEach
    void setUp() {
        brandConverter = new BrandEntityToBrandBeanConverter();
        converter = new VehicleEntityToVehicleModelBeanConverter(brandConverter);
    }

    @Test
    void convert_ShouldMapAllFieldsCorrectly() {
        // Given
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setManufacturerWMI("TOY");
        brandEntity.setCommercialBrandName("Toyota");
        brandEntity.setManufacturer("Toyota Motor Corporation");
        brandEntity.setCountry("US");

        VehicleEntity entity = new VehicleEntity();
        entity.setId(1L);
        entity.setBrand(brandEntity);
        entity.setLine("Corolla");
        entity.setModel("Corolla");
        entity.setType(VehicleTypeEntity.Sedan);
        entity.setInceptionYear(1966);
        entity.setConstructionYear(1966);
        entity.setCommercialization(Instant.parse("1966-11-01T00:00:00Z"));
        entity.setRetirement(null);

        // When
        VehicleModelBean result = converter.convert(entity);

        // Then
        assertNotNull(result);
        assertNotNull(result.getBrand());
        assertEquals("Toyota", result.getBrand().getCommercialBrandName());
        assertEquals(Optional.of("Corolla"), result.getLine());
        assertEquals("Corolla", result.getModel());
        assertEquals(VehicleType.Sedan, result.getType());
        assertEquals(1966, result.getInceptionYear());
        assertEquals(1966, result.getConstruction());
        assertTrue(result.getCommercialization().isPresent());
        assertEquals(Instant.parse("1966-11-01T00:00:00Z"), result.getCommercialization().get());
        assertFalse(result.getRetirement().isPresent());
    }

    @Test
    void convert_ShouldMapMarketDataAndParts() {
        // Given
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setManufacturerWMI("TOY");
        brandEntity.setCommercialBrandName("Toyota");
        brandEntity.setManufacturer("Toyota Motor Corporation");
        brandEntity.setCountry("US");

        VehicleEntity entity = new VehicleEntity();
        entity.setId(1L);
        entity.setBrand(brandEntity);
        entity.setModel("Corolla");
        entity.setType(VehicleTypeEntity.Sedan);
        entity.setInceptionYear(1966);
        entity.setConstructionYear(1966);

        VehicleMarketDataEntity marketData = new VehicleMarketDataEntity();
        marketData.setCountry("US");
        marketData.setCommercialName("Corolla");
        marketData.setCurrency("USD");
        marketData.setLocalMarketPrice(20000.0f);
        marketData.setMarketPrice(22000.0f);
        marketData.setMargin(10.0f);
        entity.getMarketData().add(marketData);

        VehiclePartEntity part = new VehiclePartEntity();
        part.setPartNumber("TRD-001");
        part.setName("Engine Oil Filter");
        part.setMountingReference("Engine Bay");
        part.setMarketPrice(25.0f);
        part.setMargin(8.0f);
        entity.getParts().add(part);

        // When
        VehicleModelBean result = converter.convert(entity);

        // Then
        assertNotNull(result);
        assertNotNull(result.getMarketData());
        assertEquals(1, result.getMarketData().size());
        MarketDataBean marketDataBean = result.getMarketData().get("US");
        assertNotNull(marketDataBean);
        assertEquals("Corolla", marketDataBean.getCommercialName());
        assertEquals(java.util.Currency.getInstance("USD"), marketDataBean.getCurrency());

        assertNotNull(result.getParts());
        assertEquals(1, result.getParts().size());
        VehiclePartBean partBean = result.getParts().get("TRD-001");
        assertNotNull(partBean);
        assertEquals("Engine Oil Filter", partBean.getName());
    }

    @Test
    void convert_ShouldNotAccessAssociationsOfUnselectedSections() {
        // Associations that would fail if accessed, like uninitialized lazy ones
        VehicleEntity entity = new VehicleEntity() {
            @Override
            public BrandEntity getBrand() {
                throw new AssertionError("brand accessed");
            }

            @Override
            public List<VehicleMarketDataEntity> getMarketData() {
                throw new AssertionError("market data accessed");
            }

            @Override
            public List<VehiclePartEntity> getParts() {
                throw new AssertionError("parts accessed");
            }
        };
        entity.setModel("Corolla");
        entity.setType(VehicleTypeEntity.Sedan);

        // When
        VehicleModelBean result = converter.convert(entity, ModelSelection.parse("E"));

        // Then
        assertEquals("Corolla", result.getModel());
        assertNull(result.getBrand());
        assertNull(result.getMarketData());
        assertNull(result.getParts());
    }

    @Test
    void convert_ShouldConvertOnlySelectedAssociations() {
        BrandEntity brandEntity = new BrandEntity();
        brandEntity.setCommercialBrandName("Toyota");
        VehicleEntity entity = new VehicleEntity();
        entity.setBrand(brandEntity);
        VehiclePartEntity part = new VehiclePartEntity();
        part.setPartNumber("TRD-001");
        entity.getParts().add(part);
        VehicleMarketDataEntity marketData = new VehicleMarketDataEntity();
        marketData.setCountry("US");
        entity.getMarketData().add(marketData);

        VehicleModelBean result = converter.convert(entity, ModelSelection.parse("P"));

        assertNull(result.getBrand());
        assertNull(result.getMarketData());
        assertEquals(1, result.getParts().size());
    }

    @Test
    void convert_ShouldAcceptMarketDataWithoutCurrency() {
        VehicleEntity entity = new VehicleEntity();
        VehicleMarketDataEntity marketData = new VehicleMarketDataEntity();
        marketData.setCountry("US");
        entity.getMarketData().add(marketData);

        VehicleModelBean result = converter.convert(entity);

        assertNull(result.getMarketData().get("US").getCurrency());
    }

    @Test
    void convert_ShouldHandleNullInput() {
        // When
        VehicleModelBean result = converter.convert(null);

        // Then
        assertNotNull(result);
    }
}
