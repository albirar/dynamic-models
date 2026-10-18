package cat.albirar.dynmod.demo.controllers;

import org.springframework.core.convert.ConversionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import cat.albirar.dynmod.demo.criteria.VehicleSearchCriteria;
import cat.albirar.dynmod.demo.entity.VehicleEntity;
import cat.albirar.dynmod.demo.entity.VehicleTypeEntity;
import cat.albirar.dynmod.demo.models.VehicleModelBean;
import cat.albirar.dynmod.demo.response.PagedResponse;
import cat.albirar.dynmod.demo.service.VehicleService;

@RestController
@RequestMapping("/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;
    private final ConversionService conversionService;

    public VehicleController(VehicleService vehicleService, ConversionService conversionService) {
        this.vehicleService = vehicleService;
        this.conversionService = conversionService;
    }

    @GetMapping
    public PagedResponse<VehicleModelBean> searchVehicles(
            @RequestParam(required = false) String brandManufacturerWMI,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) VehicleTypeEntity type,
            @RequestParam(required = false) String commercialNamePattern,
            Pageable pageable) {

        VehicleSearchCriteria criteria = new VehicleSearchCriteria();
        criteria.setBrandManufacturerWMI(brandManufacturerWMI);
        criteria.setCountry(country);
        criteria.setType(type);
        criteria.setCommercialNamePattern(commercialNamePattern);

        Page<VehicleEntity> page = vehicleService.searchVehicles(criteria, pageable);

        PagedResponse<VehicleModelBean> response = new PagedResponse<>();
        response.setTotalElements(page.getTotalElements());
        response.setContent(page.getContent().stream()
                .map(entity -> conversionService.convert(entity, VehicleModelBean.class))
                .toList());

        return response;
    }

    @GetMapping("/old")
    public void getVehicle(
            @RequestParam Integer size,
            @RequestParam Integer page) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
