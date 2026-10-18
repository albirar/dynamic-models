package cat.albirar.dynmod.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.repository.VehicleRepository;
import cat.albirar.dynmod.demo.specifications.VehicleSpecifications;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public Page<VehicleEntity> searchVehicles(VehicleSearchCriteria criteria, Pageable pageable) {
        Specification<VehicleEntity> spec = Specification.where(
            VehicleSpecifications.byBrandManufacturerWMI(criteria.getBrandManufacturerWMI())
        ).and(
            VehicleSpecifications.byBrandCountry(criteria.getCountry())
        ).and(
            VehicleSpecifications.byType(criteria.getType())
        ).and(
            VehicleSpecifications.byCommercialNamePattern(criteria.getCommercialNamePattern())
        );
        
        return vehicleRepository.findAll(spec, pageable);
    }
}
