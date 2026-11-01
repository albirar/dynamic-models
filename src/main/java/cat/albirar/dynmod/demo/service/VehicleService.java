package cat.albirar.dynmod.demo.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.projection.VehicleProjection;
import cat.albirar.dynmod.demo.repository.VehicleRepository;
import cat.albirar.dynmod.demo.specifications.VehicleSpecifications;
import cat.albirar.dynmod.filtering.jpa.ProjectionExecutor;
import cat.albirar.dynmod.filtering.model.ModelPart;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final ProjectionExecutor projectionExecutor;

    public Page<VehicleEntity> searchVehicles(VehicleSearchCriteria criteria, Pageable pageable) {
        return searchVehicles(criteria, ModelSelection.full(), pageable);
    }

    /**
     * Searches vehicles loading only the associations of the selected sections: the brand is
     * fetched with the page and each selected collection with one extra query for the whole
     * page. Associations of other sections stay uninitialized and must not be accessed.
     */
    @Transactional(readOnly = true)
    public Page<VehicleEntity> searchVehicles(VehicleSearchCriteria criteria, ModelSelection selection,
            Pageable pageable) {
        Specification<VehicleEntity> spec = specification(criteria);
        if (selection.includes(ModelPart.BRAND)) {
            spec = spec.and(VehicleSpecifications.fetchBrand());
        }

        Page<VehicleEntity> page = vehicleRepository.findAll(spec, pageable);
        if (page.hasContent()) {
            List<Long> ids = page.getContent().stream().map(VehicleEntity::getId).toList();
            if (selection.includes(ModelPart.MARKET)) {
                vehicleRepository.fetchMarketData(ids);
            }
            if (selection.includes(ModelPart.PARTS)) {
                vehicleRepository.fetchParts(ids);
            }
        }
        return page;
    }

    /**
     * Searches vehicles reading only the columns of the selected sections, without loading
     * entities, and builds the response beans from the projected values.
     */
    @Transactional(readOnly = true)
    public Page<VehicleModelBean> searchVehicleModels(VehicleSearchCriteria criteria, ModelSelection selection,
            Pageable pageable) {
        return projectionExecutor
                .findAll(VehicleProjection.plan(projectionExecutor), specification(criteria), selection, pageable)
                .map(VehicleProjection::toBean);
    }

    private static Specification<VehicleEntity> specification(VehicleSearchCriteria criteria) {
        return Specification.where(
            VehicleSpecifications.byBrandManufacturerWMI(criteria.getBrandManufacturerWMI())
        ).and(
            VehicleSpecifications.byBrandCountry(criteria.getCountry())
        ).and(
            VehicleSpecifications.byType(criteria.getType())
        ).and(
            VehicleSpecifications.byCommercialNamePattern(criteria.getCommercialNamePattern())
        );
    }
}
