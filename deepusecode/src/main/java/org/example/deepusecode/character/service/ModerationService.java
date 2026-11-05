package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.entity.AiTemplate;
import org.example.deepusecode.character.entity.Moderation;
import org.example.deepusecode.character.entity.QuestionSet;
import org.example.deepusecode.character.entity.ReportTargetType;
import org.example.deepusecode.character.repository.AiTemplateRepository;
import org.example.deepusecode.character.repository.QuestionSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 内容处置（下架 / 恢复）。
 *
 * <p>下架不改动 {@code visibility}——作者的意愿和平台处置分开记录，
 * 恢复时作者原来的公开状态还在，不需要他再手动公开一次。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final int NOT_DELETED = 0;

    private final QuestionSetRepository setRepository;
    private final AiTemplateRepository templateRepository;

    /** 被举报 / 被下架对象的最小信息 */
    public record TargetInfo(String title, Long ownerId, boolean takenDown, boolean exists) {

        static TargetInfo missing() {
            return new TargetInfo("（内容已删除）", null, false, false);
        }
    }

    @Transactional
    public void takeDown(String targetType, Long targetId, String reason, Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        if (ReportTargetType.SET.equals(targetType)) {
            QuestionSet set = setRepository.findByIdAndDeleted(targetId, NOT_DELETED)
                    .orElseThrow(() -> BusinessException.notFound("题集不存在或已删除"));
            set.setTakenDown(Moderation.TAKEN_DOWN);
            set.setTakedownReason(truncate(reason));
            set.setTakenDownAt(now);
            setRepository.save(set);
        } else {
            AiTemplate template = templateRepository.findByIdAndDeleted(targetId, NOT_DELETED)
                    .orElseThrow(() -> BusinessException.notFound("模板不存在或已删除"));
            template.setTakenDown(Moderation.TAKEN_DOWN);
            template.setTakedownReason(truncate(reason));
            template.setTakenDownAt(now);
            templateRepository.save(template);
        }
        log.info("内容已下架: type={}, id={}, adminId={}, reason={}", targetType, targetId, adminId, reason);
    }

    @Transactional
    public void restore(String targetType, Long targetId, Long adminId) {
        if (ReportTargetType.SET.equals(targetType)) {
            QuestionSet set = setRepository.findByIdAndDeleted(targetId, NOT_DELETED)
                    .orElseThrow(() -> BusinessException.notFound("题集不存在或已删除"));
            set.setTakenDown(Moderation.NOT_TAKEN_DOWN);
            set.setTakedownReason(null);
            set.setTakenDownAt(null);
            setRepository.save(set);
        } else {
            AiTemplate template = templateRepository.findByIdAndDeleted(targetId, NOT_DELETED)
                    .orElseThrow(() -> BusinessException.notFound("模板不存在或已删除"));
            template.setTakenDown(Moderation.NOT_TAKEN_DOWN);
            template.setTakedownReason(null);
            template.setTakenDownAt(null);
            templateRepository.save(template);
        }
        log.info("内容已恢复: type={}, id={}, adminId={}", targetType, targetId, adminId);
    }

    @Transactional(readOnly = true)
    public TargetInfo describe(String targetType, Long targetId) {
        if (ReportTargetType.SET.equals(targetType)) {
            return setRepository.findById(targetId)
                    .map(s -> new TargetInfo(s.getTitle(), s.getOwnerId(),
                            Moderation.isTakenDown(s.getTakenDown()), true))
                    .orElse(TargetInfo.missing());
        }
        return templateRepository.findById(targetId)
                .map(t -> new TargetInfo(t.getName(), t.getOwnerId(),
                        Moderation.isTakenDown(t.getTakenDown()), true))
                .orElse(TargetInfo.missing());
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
    }
}
