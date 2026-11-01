package cat.albirar.dynmod.demo.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.VehicleService;
import cat.albirar.dynmod.filtering.model.ModelSelection;
import cat.albirar.dynmod.filtering.web.ModelFiltering;

@RestController
@RequestMapping("/vehicles")
@ModelFiltering
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public PagedResponse<VehicleModelBean> searchVehicles(
            @RequestParam(required = false) String brandManufacturerWMI,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) VehicleTypeEntity type,
            @RequestParam(required = false) String commercialNamePattern,
            ModelSelection selection,
            Pageable pageable) {

        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setBrandManufacturerWMI(brandManufacturerWMI);
        criteria.setCountry(country);
        criteria.setType(type);
        criteria.setCommercialNamePattern(commercialNamePattern);

        // Only the columns of the selected sections are read from the database
        Page<VehicleModelBean> page = vehicleService.searchVehicleModels(criteria, selection, pageable);

        PagedResponse<VehicleModelBean> response = new PagedResponse<>();
        response.setTotalElements(page.getTotalElements());
        response.setContent(page.getContent());

        return response;
    }
}
