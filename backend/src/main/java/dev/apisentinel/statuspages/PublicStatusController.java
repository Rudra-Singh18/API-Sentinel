package dev.apisentinel.statuspages;

import dev.apisentinel.statuspages.StatusPageDtos.PublicStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
public class PublicStatusController {
    private final StatusPageService service;

    public PublicStatusController(StatusPageService service) { this.service = service; }

    @GetMapping("/{slug}")
    public PublicStatusResponse get(@PathVariable String slug) { return service.publicPage(slug); }
}