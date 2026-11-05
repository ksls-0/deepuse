package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.HandleReportRequest;
import org.example.deepusecode.character.dto.ReportVO;
import org.example.deepusecode.character.dto.TakedownRequest;
import org.example.deepusecode.character.entity.ReportTargetType;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.ModerationService;
import org.example.deepusecode.character.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台。整个 /api/admin/** 在 SecurityConfig 里要求 ADMIN 权限。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ReportService reportService;
    private final ModerationService moderationService;

    /** 审核队列：待处理举报 */
    @GetMapping("/reports/pending")
    public ApiResponse<PageResult<ReportVO>> pending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reportService.listPending(page, size));
    }

    /** 已处理历史 */
    @GetMapping("/reports/handled")
    public ApiResponse<PageResult<ReportVO>> handled(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reportService.listHandled(page, size));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(Map.of("pendingReports", reportService.pendingCount()));
    }

    /** 处理一条举报：action = TAKEDOWN | REJECT */
    @PostMapping("/reports/{id}/handle")
    public ApiResponse<Void> handle(@PathVariable Long id,
                                    @Valid @RequestBody HandleReportRequest request) {
        reportService.handle(CurrentUser.id(), id, request);
        return ApiResponse.success("已处理", null);
    }

    /** 不经过举报，直接下架某条内容 */
    @PostMapping("/{targetType}/{targetId}/takedown")
    public ApiResponse<Void> takeDown(@PathVariable String targetType,
                                      @PathVariable Long targetId,
                                      @Valid @RequestBody TakedownRequest request) {
        moderationService.takeDown(normalize(targetType), targetId,
                request.getReason(), CurrentUser.id());
        return ApiResponse.success("已下架", null);
    }

    /** 恢复被下架的内容（恢复后回到作者原来的公开状态） */
    @PostMapping("/{targetType}/{targetId}/restore")
    public ApiResponse<Void> restore(@PathVariable String targetType,
                                     @PathVariable Long targetId) {
        moderationService.restore(normalize(targetType), targetId, CurrentUser.id());
        return ApiResponse.success("已恢复", null);
    }

    private String normalize(String targetType) {
        String value = targetType == null ? "" : targetType.trim().toUpperCase();
        if (!ReportTargetType.isValid(value)) {
            throw BusinessException.badRequest("内容类型只能是 SET 或 TEMPLATE");
        }
        return value;
    }
}
