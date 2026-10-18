package cat.albirar.dynmod.demo.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.VehicleService;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleService vehicleService;

    @Mock
    private ConversionService conversionService;

    private VehicleController vehicleController;

    private VehicleEntity toyotaCorollaEntity;
    private VehicleModelBean toyotaCorollaBean;
    private BrandEntity toyotaBrandEntity;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        vehicleController = new VehicleController(vehicleService, conversionService);
        
        toyotaBrandEntity = new BrandEntity();
        toyotaBrandEntity.setManufacturerWMI("TOY");
        toyotaBrandEntity.setCommercialBrandName("Toyota");
        toyotaBrandEntity.setManufacturer("Toyota Motor Corporation");
        toyotaBrandEntity.setCountry("US");

        toyotaCorollaEntity = new VehicleEntity();
        toyotaCorollaEntity.setId(1L);
        toyotaCorollaEntity.setBrand(toyotaBrandEntity);
        toyotaCorollaEntity.setModel("Corolla");
        toyotaCorollaEntity.setType(VehicleTypeEntity.Sedan);
        toyotaCorollaEntity.setInceptionYear(1966);
        toyotaCorollaEntity.setConstructionYear(1966);

        toyotaCorollaBean = new VehicleModelBean();
        toyotaCorollaBean.setModel("Corolla");

        pageable = PageRequest.of(0, 2);
    }

    @Test
    void searchVehicles_WithBrandManufacturerWMI_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleEntity> page = new PageImpl<>(List.of(toyotaCorollaEntity));
        when(vehicleService.searchVehicles(
                new VehicleSearchCriteria() {{ setBrandManufacturerWMI("TOY"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaCorollaEntity, VehicleModelBean.class)).thenReturn(toyotaCorollaBean);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                "TOY", null, null, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Corolla", result.getContent().get(0).getModel());
    }

    @Test
    void searchVehicles_WithCountry_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleEntity> page = new PageImpl<>(List.of(toyotaCorollaEntity));
        when(vehicleService.searchVehicles(
                new VehicleSearchCriteria() {{ setCountry("JP"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaCorollaEntity, VehicleModelBean.class)).thenReturn(toyotaCorollaBean);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, "JP", null, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithType_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleEntity> page = new PageImpl<>(List.of(toyotaCorollaEntity));
        when(vehicleService.searchVehicles(
                new VehicleSearchCriteria() {{ setType(VehicleTypeEntity.Sedan); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaCorollaEntity, VehicleModelBean.class)).thenReturn(toyotaCorollaBean);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, VehicleTypeEntity.Sedan, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithCommercialNamePattern_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleEntity> page = new PageImpl<>(List.of(toyotaCorollaEntity));
        when(vehicleService.searchVehicles(
                new VehicleSearchCriteria() {{ setCommercialNamePattern("Corolla"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaCorollaEntity, VehicleModelBean.class)).thenReturn(toyotaCorollaBean);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, null, "Corolla", pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithNoFilters_ShouldReturnAllResults() {
        // Given
        Page<VehicleEntity> page = new PageImpl<>(List.of(toyotaCorollaEntity));
        when(vehicleService.searchVehicles(
                new VehicleSearchCriteria(),
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaCorollaEntity, VehicleModelBean.class)).thenReturn(toyotaCorollaBean);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, null, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }
}
