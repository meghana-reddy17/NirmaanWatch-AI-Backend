package com.nirmaanwatch.backend.controller;

import com.nirmaanwatch.backend.dto.ApiDtos;
import com.nirmaanwatch.backend.entity.CitizenReport;
import com.nirmaanwatch.backend.entity.ReportStatus;
import com.nirmaanwatch.backend.entity.UserAccount;
import com.nirmaanwatch.backend.service.CurrentUserService;
import com.nirmaanwatch.backend.service.DtoMapper;
import com.nirmaanwatch.backend.service.EvidenceService;
import com.nirmaanwatch.backend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;
    private final EvidenceService evidenceService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService, EvidenceService evidenceService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
        this.evidenceService = evidenceService;
    }

    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ReportCreatedDto submitJson(@Valid @RequestBody ApiDtos.CreateReportRequest payload) {
        UserAccount reporter = currentUserService.findCurrent().orElse(null);
        return reportService.submit(payload, null, reporter);
    }

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ReportCreatedDto submit(
            @Valid @RequestPart("payload") ApiDtos.CreateReportRequest payload,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        UserAccount reporter = currentUserService.findCurrent().orElse(null);
        return reportService.submit(payload, file, reporter);
    }

    @GetMapping("/track")
    public ApiDtos.ReportDto track(@RequestParam String ref, @RequestParam String token) {
        CitizenReport report = reportService.track(ref, token);
        return DtoMapper.toReport(report, reportService.evidenceFor(report.getId()), reportService.responsesFor(report.getId()));
    }

    @GetMapping("/mine")
    public List<ApiDtos.ReportDto> mine() {
        UserAccount user = currentUserService.requireCurrent();
        return reportService.mine(user).stream()
                .map(r -> DtoMapper.toReport(r, reportService.evidenceFor(r.getId()), reportService.responsesFor(r.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    public ApiDtos.ReportDto get(@PathVariable Long id) {
        UserAccount user = currentUserService.requireCurrent();
        CitizenReport report = reportService.requireAccessible(id, user);
        return DtoMapper.toReport(report, reportService.evidenceFor(id), reportService.responsesFor(id));
    }

    @PatchMapping("/{id}/status")
    public ApiDtos.ReportDto status(@PathVariable Long id, @Valid @RequestBody ApiDtos.ReportStatusRequest request) {
        UserAccount actor = currentUserService.requireCurrent();
        CitizenReport report = reportService.updateStatus(id, request.status(), actor);
        return DtoMapper.toReport(report, reportService.evidenceFor(id), reportService.responsesFor(id));
    }

    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.ReportResponseDto respond(@PathVariable Long id, @Valid @RequestBody ApiDtos.ReportResponseRequest request) {
        UserAccount actor = currentUserService.requireCurrent();
        return DtoMapper.toResponse(reportService.addResponse(id, request.message(), actor));
    }

    @PostMapping("/{id}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.EvidenceMetaDto upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        UserAccount actor = currentUserService.requireCurrent();
        CitizenReport report = reportService.requireAccessible(id, actor);
        return DtoMapper.toEvidence(evidenceService.store(report, file, actor));
    }
}
