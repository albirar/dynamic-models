package cat.albirar.dynmod.demo.repository;

import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;

@Repository
public interface VehicleRepository extends JpaRepository<VehicleEntity, Long>, JpaSpecificationExecutor<VehicleEntity> {
    Page<VehicleEntity> findByBrand_ManufacturerWMI(String brandWmi, Pageable pageable);
    Page<VehicleEntity> findByBrand_Country(String country, Pageable pageable);
    Page<VehicleEntity> findByType(VehicleTypeEntity type, Pageable pageable);
    Page<VehicleEntity> findByMarketData_CommercialNameContainingIgnoreCase(String commercialName, Pageable pageable);
}
