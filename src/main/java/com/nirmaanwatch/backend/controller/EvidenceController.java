package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.config.AppProperties;
import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.EvidenceFile;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.exception.ApiException;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.EvidenceService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidenceController {

    private final EvidenceService evidenceService;
    private final CurrentUserService currentUserService;
    private final AppProperties properties;

    public EvidenceController(EvidenceService evidenceService, CurrentUserService currentUserService, AppProperties properties) {
        this.evidenceService = evidenceService;
        this.currentUserService = currentUserService;
        this.properties = properties;
    }

    @GetMapping("/api/evidence/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable Long id) {
        EvidenceFile file = evidenceService.getById(id);
        UserAccount user = currentUserService.findCurrent().orElse(null);
        if (!evidenceService.canAccess(file, user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to download this evidence file.");
        }
        FileSystemResource resource = new FileSystemResource(evidenceService.resolveFile(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .body(resource);
    }

    @GetMapping("/api/evidence/{id}/analysis")
    public ApiDtos.EvidenceAnalysisDto analysis(@PathVariable Long id) {
        EvidenceFile file = evidenceService.getById(id);
        UserAccount user = currentUserService.requireCurrent();
        if (!evidenceService.canAccess(file, user)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not allowed to inspect this evidence file.");
        }
        if ("none".equalsIgnoreCase(properties.getAiProvider())) {
            return new ApiDtos.EvidenceAnalysisDto("UNAVAILABLE",
                    "Automated evidence analysis is not configured. Review the file manually. No image or document contents were processed.");
        }
        return new ApiDtos.EvidenceAnalysisDto("UNAVAILABLE",
                "An AI provider setting is present, but this deployment does not call an external model. Review the file manually.");
    }
}
