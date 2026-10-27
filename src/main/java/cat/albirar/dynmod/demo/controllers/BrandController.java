package cat.albirar.dynmod.demo.controllers;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cat.albirar.dynmod.demo.criteria.BrandSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.models.BrandBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.BrandService;
import cat.albirar.dynmod.filtering.web.ModelFiltering;

@RestController
@RequestMapping("/brands")
@ModelFiltering
public class BrandController {

    private final BrandService brandService;
    private final ConversionService conversionService;

    public BrandController(BrandService brandService, ConversionService conversionService) {
        this.brandService = brandService;
        this.conversionService = conversionService;
    }

    @GetMapping
    public PagedResponse<BrandBean> searchBrands(
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String commercialBrandNamePattern,
            Pageable pageable) {

        BrandSearchCriteria criteria = new BrandSearchCriteria();
        criteria.setManufacturer(manufacturer);
        criteria.setCountry(country);
        criteria.setCommercialBrandNamePattern(commercialBrandNamePattern);

        Page<BrandEntity> page = brandService.searchBrands(criteria, pageable);

        PagedResponse<BrandBean> response = new PagedResponse<>();
        response.setTotalElements(page.getTotalElements());
        response.setContent(page.getContent().stream()
                .map(entity -> conversionService.convert(entity, BrandBean.class))
                .toList());

        return response;
    }
}
