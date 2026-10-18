package cat.albirar.dynmod.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.repository.VehicleRepository;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @InjectMocks
    private VehicleService vehicleService;

    private VehicleEntity toyotaCorollaEntity;
    private VehicleEntity fordMustangEntity;
    private BrandEntity toyotaBrandEntity;
    private BrandEntity fordBrandEntity;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        toyotaBrandEntity = new BrandEntity();
        toyotaBrandEntity.setManufacturerWMI("TOY");
        toyotaBrandEntity.setCommercialBrandName("Toyota");
        toyotaBrandEntity.setManufacturer("Toyota Motor Corporation");
        toyotaBrandEntity.setCountry("JP");

        fordBrandEntity = new BrandEntity();
        fordBrandEntity.setManufacturerWMI("FRD");
        fordBrandEntity.setCommercialBrandName("Ford");
        fordBrandEntity.setManufacturer("Ford Motor Company");
        fordBrandEntity.setCountry("US");

        toyotaCorollaEntity = new VehicleEntity();
        toyotaCorollaEntity.setId(1L);
        toyotaCorollaEntity.setBrand(toyotaBrandEntity);
        toyotaCorollaEntity.setModel("Corolla");
        toyotaCorollaEntity.setType(VehicleTypeEntity.Sedan);
        toyotaCorollaEntity.setInceptionYear(1966);
        toyotaCorollaEntity.setConstructionYear(1966);

        fordMustangEntity = new VehicleEntity();
        fordMustangEntity.setId(2L);
        fordMustangEntity.setBrand(fordBrandEntity);
        fordMustangEntity.setModel("Mustang");
        fordMustangEntity.setType(VehicleTypeEntity.Coupe);
        fordMustangEntity.setInceptionYear(1964);
        fordMustangEntity.setConstructionYear(1964);

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void searchVehicles_ByBrandManufacturerWMI_ShouldReturnFilteredResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setBrandManufacturerWMI("TOY");

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Corolla", result.getContent().get(0).getModel());
    }

    @Test
    void searchVehicles_ByCountry_ShouldReturnFilteredResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setCountry("JP");

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchVehicles_ByType_ShouldReturnFilteredResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setType(VehicleTypeEntity.Sedan);

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchVehicles_ByCommercialNamePattern_ShouldReturnFilteredResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setCommercialNamePattern("Corolla");

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchVehicles_WithNoCriteria_ShouldReturnAllResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity, fordMustangEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void searchVehicles_WithMultipleCriteria_ShouldReturnFilteredResults() {
        // Given
        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setBrandManufacturerWMI("TOY");
        criteria.setType(VehicleTypeEntity.Sedan);

        Page<VehicleEntity> page = new PageImpl<>(java.util.List.of(toyotaCorollaEntity));
        when(vehicleRepository.findAll(ArgumentMatchers.<Specification<VehicleEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<VehicleEntity> result = vehicleService.searchVehicles(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Corolla", result.getContent().get(0).getModel());
    }
}
