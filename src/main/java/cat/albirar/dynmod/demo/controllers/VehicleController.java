package cat.albirar.dynmod.demo.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class VehicleController {

    @GetMapping("/vehicle")
    public void getVehicle(
            @RequestParam(required = true) int size,
            @RequestParam(required = true) int page) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
