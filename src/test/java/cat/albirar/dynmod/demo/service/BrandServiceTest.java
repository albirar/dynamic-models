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

import cat.albirar.dynmod.demo.criteria.BrandSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.repository.BrandRepository;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private BrandEntity toyotaEntity;
    private BrandEntity fordEntity;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        toyotaEntity = new BrandEntity();
        toyotaEntity.setManufacturerWMI("TOY");
        toyotaEntity.setCommercialBrandName("Toyota");
        toyotaEntity.setManufacturer("Toyota Motor Corporation");
        toyotaEntity.setCountry("JP");

        fordEntity = new BrandEntity();
        fordEntity.setManufacturerWMI("FRD");
        fordEntity.setCommercialBrandName("Ford");
        fordEntity.setManufacturer("Ford Motor Company");
        fordEntity.setCountry("US");

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void searchBrands_ByManufacturer_ShouldReturnFilteredResults() {
        // Given
        BrandSearchCriteria criteria = new BrandSearchCriteria();
        criteria.setManufacturer("Toyota Motor Corporation");

        Page<BrandEntity> page = new PageImpl<>(java.util.List.of(toyotaEntity));
        when(brandRepository.findAll(ArgumentMatchers.<Specification<BrandEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<BrandEntity> result = brandService.searchBrands(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("TOY", result.getContent().get(0).getManufacturerWMI());
    }

    @Test
    void searchBrands_ByCountry_ShouldReturnFilteredResults() {
        // Given
        BrandSearchCriteria criteria = new BrandSearchCriteria();
        criteria.setCountry("JP");

        Page<BrandEntity> page = new PageImpl<>(java.util.List.of(toyotaEntity));
        when(brandRepository.findAll(ArgumentMatchers.<Specification<BrandEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<BrandEntity> result = brandService.searchBrands(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Toyota", result.getContent().get(0).getCommercialBrandName());
    }

    @Test
    void searchBrands_ByCommercialBrandNamePattern_ShouldReturnFilteredResults() {
        // Given
        BrandSearchCriteria criteria = new BrandSearchCriteria();
        criteria.setCommercialBrandNamePattern("Toy");

        Page<BrandEntity> page = new PageImpl<>(java.util.List.of(toyotaEntity));
        when(brandRepository.findAll(ArgumentMatchers.<Specification<BrandEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<BrandEntity> result = brandService.searchBrands(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void searchBrands_WithNoCriteria_ShouldReturnAllResults() {
        // Given
        BrandSearchCriteria criteria = new BrandSearchCriteria();

        Page<BrandEntity> page = new PageImpl<>(java.util.List.of(toyotaEntity, fordEntity));
        when(brandRepository.findAll(ArgumentMatchers.<Specification<BrandEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<BrandEntity> result = brandService.searchBrands(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
    }

    @Test
    void searchBrands_WithMultipleCriteria_ShouldReturnFilteredResults() {
        // Given
        BrandSearchCriteria criteria = new BrandSearchCriteria();
        criteria.setManufacturer("Toyota Motor Corporation");
        criteria.setCountry("JP");

        Page<BrandEntity> page = new PageImpl<>(java.util.List.of(toyotaEntity));
        when(brandRepository.findAll(ArgumentMatchers.<Specification<BrandEntity>>any(), eq(pageable))).thenReturn(page);

        // When
        Page<BrandEntity> result = brandService.searchBrands(criteria, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("TOY", result.getContent().get(0).getManufacturerWMI());
    }
}
