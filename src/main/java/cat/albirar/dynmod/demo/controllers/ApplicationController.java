package cat.albirar.dynmod.demo.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cat.albirar.dynmod.demo.config.ApplicationProperties;

@RestController
@RequestMapping
public class ApplicationController {

    private final ApplicationProperties applicationProperties;

    public ApplicationController(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @GetMapping("/appVersion")
    public ApplicationProperties getAppVersion() {
        return applicationProperties;
    }
}
