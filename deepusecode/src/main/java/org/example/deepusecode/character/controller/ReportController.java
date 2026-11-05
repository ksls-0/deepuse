package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.dto.ReportRequest;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.ReportService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报公开内容。任何登录用户都可以举报，同一个人对同一内容只能举报一次。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResponse<Void> report(@Valid @RequestBody ReportRequest request) {
        reportService.report(CurrentUser.id(), request);
        return ApiResponse.success("举报已提交，我们会尽快处理", null);
    }
}
