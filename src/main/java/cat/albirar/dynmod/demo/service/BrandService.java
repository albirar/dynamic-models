package cat.albirar.dynmod.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import cat.albirar.dynmod.demo.criteria.BrandSearchCriteria;
import cat.albirar.dynmod.demo.entity.BrandEntity;
import cat.albirar.dynmod.demo.repository.BrandRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    public Page<BrandEntity> searchBrands(BrandSearchCriteria criteria, Pageable pageable) {
        if (criteria.getManufacturer() != null) {
            return brandRepository.findByManufacturer(criteria.getManufacturer(), pageable);
        } else if (criteria.getCountry() != null) {
            return brandRepository.findByCountry(criteria.getCountry(), pageable);
        } else if (criteria.getCommercialBrandNamePattern() != null) {
            return brandRepository.findByCommercialBrandNameContainingIgnoreCase(
                criteria.getCommercialBrandNamePattern(), pageable);
        }
        return brandRepository.findAll(pageable);
    }
}
