package cat.albirar.dynmod.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import cat.albirar.dynmod.demo.entity.BrandEntity;

public interface BrandRepository extends JpaRepository<BrandEntity, String>, JpaSpecificationExecutor<BrandEntity> {
    Page<BrandEntity> findByCountry(String country, Pageable pageable);
    Page<BrandEntity> findByManufacturer(String manufacturer, Pageable pageable);
    Page<BrandEntity> findByCommercialBrandNameContainingIgnoreCase(String commercialBrandName, Pageable pageable);
}
