package cat.albirar.dynmod.demo.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;

    public Page<VehicleEntity> searchVehicles(VehicleSearchCriteria criteria, Pageable pageable) {
        if (criteria.getBrandManufacturerWMI() != null) {
            return vehicleRepository.findByBrand_ManufacturerWMI(criteria.getBrandManufacturerWMI(), pageable);
        } else if (criteria.getCountry() != null) {
            return vehicleRepository.findByBrand_Country(criteria.getCountry(), pageable);
        } else if (criteria.getType() != null) {
            return vehicleRepository.findByType(criteria.getType(), pageable);
        } else if (criteria.getCommercialNamePattern() != null) {
            return vehicleRepository.findByMarketData_CommercialNameContainingIgnoreCase(
                criteria.getCommercialNamePattern(), pageable);
        }
        return vehicleRepository.findAll(pageable);
    }
}
