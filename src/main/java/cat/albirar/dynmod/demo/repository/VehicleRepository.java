package cat.albirar.dynmod.demo.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long>, JpaSpecificationExecutor<VehicleEntity> {
    Page<VehicleEntity> findByBrand_ManufacturerWMI(String brandWmi, Pageable pageable);
    Page<VehicleEntity> findByBrand_Country(String country, Pageable pageable);
    Page<VehicleEntity> findByType(VehicleTypeEntity type, Pageable pageable);
    Page<VehicleEntity> findByMarketData_CommercialNameContainingIgnoreCase(String commercialName, Pageable pageable);

    /**
     * Initializes the market data of vehicles already loaded in the persistence context with a
     * single query. Collections are fetched one per query: fetching both bags at once is not
     * allowed, and fetching them in the paged query would paginate in memory.
     */
    @Query("select v from VehicleEntity v left join fetch v.marketData where v.id in :ids")
    List<VehicleEntity> fetchMarketData(@Param("ids") Collection<Long> ids);

    /**
     * Initializes the parts of vehicles already loaded in the persistence context with a single query.
     */
    @Query("select v from VehicleEntity v left join fetch v.parts where v.id in :ids")
    List<VehicleEntity> fetchParts(@Param("ids") Collection<Long> ids);
}
