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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.VehicleService;
import cat.albirar.dynmod.filtering.model.ModelSelection;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    private static final ModelSelection SELECTION = ModelSelection.parse("B");

    @Mock
    private VehicleService vehicleService;

    private VehicleController vehicleController;

    private VehicleModelBean toyotaCorollaBean;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        vehicleController = new VehicleController(vehicleService);

        toyotaCorollaBean = new VehicleModelBean();
        toyotaCorollaBean.setModel("Corolla");

        pageable = PageRequest.of(0, 2);
    }

    @Test
    void searchVehicles_WithBrandManufacturerWMI_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleModelBean> page = new PageImpl<>(List.of(toyotaCorollaBean));
        when(vehicleService.searchVehicleModels(
                new VehicleSearchCriteria() {{ setBrandManufacturerWMI("TOY"); }},
                SELECTION, pageable))
            .thenReturn(page);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                "TOY", null, null, null, SELECTION, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Corolla", result.getContent().get(0).getModel());
    }

    @Test
    void searchVehicles_WithCountry_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleModelBean> page = new PageImpl<>(List.of(toyotaCorollaBean));
        when(vehicleService.searchVehicleModels(
                new VehicleSearchCriteria() {{ setCountry("JP"); }},
                SELECTION, pageable))
            .thenReturn(page);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, "JP", null, null, SELECTION, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithType_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleModelBean> page = new PageImpl<>(List.of(toyotaCorollaBean));
        when(vehicleService.searchVehicleModels(
                new VehicleSearchCriteria() {{ setType(VehicleTypeEntity.Sedan); }},
                SELECTION, pageable))
            .thenReturn(page);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, VehicleTypeEntity.Sedan, null, SELECTION, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithCommercialNamePattern_ShouldReturnPagedResponse() {
        // Given
        Page<VehicleModelBean> page = new PageImpl<>(List.of(toyotaCorollaBean));
        when(vehicleService.searchVehicleModels(
                new VehicleSearchCriteria() {{ setCommercialNamePattern("Corolla"); }},
                SELECTION, pageable))
            .thenReturn(page);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, null, "Corolla", SELECTION, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchVehicles_WithNoFilters_ShouldReturnAllResults() {
        // Given
        Page<VehicleModelBean> page = new PageImpl<>(List.of(toyotaCorollaBean));
        when(vehicleService.searchVehicleModels(
                new VehicleSearchCriteria(),
                SELECTION, pageable))
            .thenReturn(page);

        // When
        PagedResponse<VehicleModelBean> result = vehicleController.searchVehicles(
                null, null, null, null, SELECTION, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }
}
