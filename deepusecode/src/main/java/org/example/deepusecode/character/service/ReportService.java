package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.HandleReportRequest;
import org.example.deepusecode.character.dto.ReportRequest;
import org.example.deepusecode.character.dto.ReportVO;
import org.example.deepusecode.character.entity.ContentReport;
import org.example.deepusecode.character.entity.ReportStatus;
import org.example.deepusecode.character.entity.ReportTargetType;
import org.example.deepusecode.character.repository.ContentReportRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报与审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String ACTION_TAKEDOWN = "TAKEDOWN";
    private static final String ACTION_REJECT = "REJECT";

    private final ContentReportRepository reportRepository;
    private final ModerationService moderationService;
    private final UserAccountResolver accountResolver;

    // ===================== 用户侧 =====================

    @Transactional
    public void report(Long reporterId, ReportRequest request) {
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim().toUpperCase();
        if (!ReportTargetType.isValid(targetType)) {
            throw BusinessException.badRequest("举报对象类型只能是 SET 或 TEMPLATE");
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw BusinessException.badRequest("请选择举报原因");
        }

        ModerationService.TargetInfo target = moderationService.describe(targetType, request.getTargetId());
        if (!target.exists()) {
            throw BusinessException.notFound("被举报的内容不存在");
        }
        if (reporterId.equals(target.ownerId())) {
            throw BusinessException.badRequest("不能举报自己发布的内容");
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetId(
                reporterId, targetType, request.getTargetId())) {
            throw BusinessException.conflict("你已经举报过该内容，无需重复提交");
        }

        ContentReport report = new ContentReport();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setReason(request.getReason().trim());
        report.setDetail(trimToNull(request.getDetail()));
        report.setStatus(ReportStatus.PENDING);
        try {
            reportRepository.save(report);
        } catch (DataIntegrityViolationException e) {
            // 并发重复提交时由唯一索引兜底
            throw BusinessException.conflict("你已经举报过该内容，无需重复提交");
        }
        log.info("收到举报: type={}, targetId={}, reporterId={}, reason={}",
                targetType, request.getTargetId(), reporterId, report.getReason());
    }

    // ===================== 管理侧 =====================

    @Transactional(readOnly = true)
    public PageResult<ReportVO> listPending(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<ContentReport> result = reportRepository.findByStatusOrderByCreatedAtAsc(
                ReportStatus.PENDING, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public PageResult<ReportVO> listHandled(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<ContentReport> result = reportRepository.findByStatusNotOrderByHandledAtDesc(
                ReportStatus.PENDING, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public long pendingCount() {
        return reportRepository.countByStatus(ReportStatus.PENDING);
    }

    /**
     * 处理一条举报。
     * <ul>
     *   <li>TAKEDOWN：下架内容，并把同一内容的其他待处理举报一并结掉</li>
     *   <li>REJECT：仅关闭这一条</li>
     * </ul>
     */
    @Transactional
    public void handle(Long adminId, Long reportId, HandleReportRequest request) {
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase();
        if (!ACTION_TAKEDOWN.equals(action) && !ACTION_REJECT.equals(action)) {
            throw BusinessException.badRequest("处理动作只能是 TAKEDOWN 或 REJECT");
        }

        ContentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> BusinessException.notFound("举报记录不存在"));
        if (!ReportStatus.PENDING.equals(report.getStatus())) {
            throw BusinessException.conflict("该举报已处理过");
        }

        LocalDateTime now = LocalDateTime.now();
        String note = trimToNull(request.getNote());

        if (ACTION_TAKEDOWN.equals(action)) {
            String reason = note != null ? note : report.getReason();
            moderationService.takeDown(report.getTargetType(), report.getTargetId(), reason, adminId);

            // 同一内容可能被多人举报，一次处置全部结掉，避免审核队列里堆重复项
            List<ContentReport> sameTarget =
                    reportRepository.findByTargetTypeAndTargetId(report.getTargetType(), report.getTargetId());
            for (ContentReport other : sameTarget) {
                if (ReportStatus.PENDING.equals(other.getStatus())) {
                    other.setStatus(ReportStatus.TAKEN_DOWN);
                    other.setHandledBy(adminId);
                    other.setHandledAt(now);
                    other.setHandleNote(note != null ? note : "内容已下架");
                    reportRepository.save(other);
                }
            }
            log.info("举报成立并下架: reportId={}, type={}, targetId={}",
                    reportId, report.getTargetType(), report.getTargetId());
        } else {
            report.setStatus(ReportStatus.REJECTED);
            report.setHandledBy(adminId);
            report.setHandledAt(now);
            report.setHandleNote(note);
            reportRepository.save(report);
            log.info("举报被驳回: reportId={}", reportId);
        }
    }

    // ===================== 内部 =====================

    private PageResult<ReportVO> toPage(Page<ContentReport> page) {
        Map<Long, String> accounts = accountResolver.resolve(
                page.getContent().stream()
                        .map(ContentReport::getReporterId)
                        .distinct()
                        .toList());
        return PageResult.of(page, r -> toVO(r, accounts));
    }

    private ReportVO toVO(ContentReport report, Map<Long, String> reporterAccounts) {
        ModerationService.TargetInfo target =
                moderationService.describe(report.getTargetType(), report.getTargetId());
        Map<Long, String> ownerAccounts = new HashMap<>();
        if (target.ownerId() != null) {
            ownerAccounts = accountResolver.resolve(List.of(target.ownerId()));
        }

        return ReportVO.builder()
                .id(report.getId())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetTitle(target.title())
                .targetOwnerId(target.ownerId())
                .targetOwnerAccount(ownerAccounts.get(target.ownerId()))
                .targetTakenDown(target.takenDown())
                .reporterId(report.getReporterId())
                .reporterAccount(reporterAccounts.get(report.getReporterId()))
                .reason(report.getReason())
                .detail(report.getDetail())
                .status(report.getStatus())
                .handledBy(report.getHandledBy())
                .handledAt(report.getHandledAt())
                .handleNote(report.getHandleNote())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
