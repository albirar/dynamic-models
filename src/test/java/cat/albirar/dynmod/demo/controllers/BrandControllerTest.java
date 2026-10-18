package cat.albirar.dynmod.demo.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import cat.albirar.dynmod.demo.criteria.BrandSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.BrandService;

@ExtendWith(MockitoExtension.class)
class BrandControllerTest {

    @Mock
    private BrandService brandService;

    @Mock
    private ConversionService conversionService;

    private BrandController brandController;

    private BrandEntity toyotaEntity;
    private BrandBean toyotaBean;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        brandController = new BrandController(brandService, conversionService);
        
        toyotaEntity = new BrandEntity();
        toyotaEntity.setManufacturerWMI("TOY");
        toyotaEntity.setCommercialBrandName("Toyota");
        toyotaEntity.setManufacturer("Toyota Motor Corporation");
        toyotaEntity.setCountry("US");

        toyotaBean = new BrandBean();
        toyotaBean.setManufacturerWMI("TOY");
        toyotaBean.setCommercialBrandName("Toyota");
        toyotaBean.setManufacturer("Toyota Motor Corporation");
        toyotaBean.setCountry("US");

        pageable = PageRequest.of(0, 2);
    }

    @Test
    void searchBrands_WithManufacturerFilter_ShouldReturnPagedResponse() {
        // Given
        Page<BrandEntity> page = new PageImpl<>(List.of(toyotaEntity));
        when(brandService.searchBrands(
                new BrandSearchCriteria() {{ setManufacturer("Toyota Motor Corporation"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaEntity, BrandBean.class)).thenReturn(toyotaBean);

        // When
        PagedResponse<BrandBean> result = brandController.searchBrands(
                "Toyota Motor Corporation", null, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("Toyota", result.getContent().get(0).getCommercialBrandName());
    }

    @Test
    void searchBrands_WithCountryFilter_ShouldReturnPagedResponse() {
        // Given
        Page<BrandEntity> page = new PageImpl<>(List.of(toyotaEntity));
        when(brandService.searchBrands(
                new BrandSearchCriteria() {{ setCountry("US"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaEntity, BrandBean.class)).thenReturn(toyotaBean);

        // When
        PagedResponse<BrandBean> result = brandController.searchBrands(
                null, "US", null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchBrands_WithCommercialBrandNamePattern_ShouldReturnPagedResponse() {
        // Given
        Page<BrandEntity> page = new PageImpl<>(List.of(toyotaEntity));
        when(brandService.searchBrands(
                new BrandSearchCriteria() {{ setCommercialBrandNamePattern("Toy"); }},
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaEntity, BrandBean.class)).thenReturn(toyotaBean);

        // When
        PagedResponse<BrandBean> result = brandController.searchBrands(
                null, null, "Toy", pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void searchBrands_WithNoFilters_ShouldReturnAllResults() {
        // Given
        Page<BrandEntity> page = new PageImpl<>(List.of(toyotaEntity));
        when(brandService.searchBrands(
                new BrandSearchCriteria(),
                pageable))
            .thenReturn(page);
        when(conversionService.convert(toyotaEntity, BrandBean.class)).thenReturn(toyotaBean);

        // When
        PagedResponse<BrandBean> result = brandController.searchBrands(
                null, null, null, pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }
}
