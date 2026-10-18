package cat.albirar.dynmod.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import cat.albirar.dynmod.demo.criteria.BrandSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.repository.BrandRepository;
import cat.albirar.dynmod.demo.specifications.BrandSpecifications;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public Page<BrandEntity> searchBrands(BrandSearchCriteria criteria, Pageable pageable) {
        Specification<BrandEntity> spec = Specification.where(
            BrandSpecifications.byManufacturer(criteria.getManufacturer())
        ).and(
            BrandSpecifications.byCountry(criteria.getCountry())
        ).and(
            BrandSpecifications.byCommercialBrandNamePattern(criteria.getCommercialBrandNamePattern())
        );
        
        return brandRepository.findAll(spec, pageable);
    }
}
