package dev.apisentinel.statuspages;

import dev.apisentinel.statuspages.StatusPageDtos.SaveStatusPageRequest;
import dev.apisentinel.statuspages.StatusPageDtos.StatusPageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status-pages")
public class StatusPageController {
    private final StatusPageService service;

    public StatusPageController(StatusPageService service) { this.service = service; }

    @GetMapping
    public List<StatusPageResponse> list(@AuthenticationPrincipal UUID ownerId) { return service.list(ownerId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StatusPageResponse create(@AuthenticationPrincipal UUID ownerId, @Valid @RequestBody SaveStatusPageRequest input) {
        return service.create(ownerId, input);
    }

    @PatchMapping("/{id}")
    public StatusPageResponse update(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id,
            @Valid @RequestBody SaveStatusPageRequest input) { return service.update(ownerId, id, input); }

    @PostMapping("/{id}/publish")
    public StatusPageResponse publish(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { return service.publish(ownerId, id, true); }

    @PostMapping("/{id}/unpublish")
    public StatusPageResponse unpublish(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { return service.publish(ownerId, id, false); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UUID ownerId, @PathVariable UUID id) { service.delete(ownerId, id); }
}