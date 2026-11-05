package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.ai.DeepSeekClient;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.ConversationVO;
import org.example.deepusecode.character.dto.MessageVO;
import org.example.deepusecode.character.dto.SendMessageResponse;
import org.example.deepusecode.character.entity.AiTemplate;
import org.example.deepusecode.character.entity.Conversation;
import org.example.deepusecode.character.entity.Message;
import org.example.deepusecode.character.repository.ConversationRepository;
import org.example.deepusecode.character.repository.MessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int NOT_DELETED = 0;
    /** 作为上下文带进模型的历史消息条数上限 */
    private static final int CONTEXT_MESSAGE_LIMIT = 20;
    /** 上下文预算（按字符粗略折算，中文约等于 1 字 1 token） */
    private static final int CONTEXT_CHAR_BUDGET = 6000;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TemplateService templateService;
    private final DeepSeekClient deepSeekClient;
    private final ChatMessageWriter messageWriter;

    @Transactional
    public ConversationVO create(Long userId, Long templateId) {
        AiTemplate template = templateService.loadUsable(userId, templateId);

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTemplateId(templateId);
        conversation.setTitle(template.getName());
        conversation.setDeleted(NOT_DELETED);
        Conversation saved = conversationRepository.save(conversation);

        templateService.increaseUseCount(templateId);

        return toVO(saved, template.getName());
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationVO> listMine(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<Conversation> result = conversationRepository
                .findByUserIdAndDeletedOrderByUpdatedAtDesc(userId, NOT_DELETED, pageable);

        List<Long> templateIds = result.getContent().stream()
                .map(Conversation::getTemplateId)
                .distinct()
                .toList();
        Map<Long, String> names = templateService.templateNames(userId, templateIds);

        return PageResult.of(result, c -> toVO(c, names.get(c.getTemplateId())));
    }

    @Transactional(readOnly = true)
    public ConversationVO detail(Long userId, Long conversationId) {
        Conversation conversation = loadOwned(userId, conversationId);
        String name = templateService
                .templateNames(userId, List.of(conversation.getTemplateId()))
                .get(conversation.getTemplateId());
        return toVO(conversation, name);
    }

    /**
     * 历史消息。beforeId 用于向上翻页；为空时按页返回，前端自行按 id 升序渲染。
     */
    @Transactional(readOnly = true)
    public PageResult<MessageVO> messages(Long userId, Long conversationId,
                                          Long beforeId, int page, int size) {
        loadOwned(userId, conversationId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));

        Page<Message> result = beforeId == null
                ? messageRepository.findByConversationIdOrderByIdAsc(conversationId, pageable)
                : messageRepository.findByConversationIdAndIdLessThanOrderByIdDesc(
                        conversationId, beforeId, pageable);

        List<MessageVO> items = result.getContent().stream()
                .sorted(Comparator.comparing(Message::getId))
                .map(this::toMessageVO)
                .toList();

        return new PageResult<>(items, result.getTotalElements(),
                result.getNumber(), result.getSize(), result.hasNext());
    }

    /**
     * 同步对话：一问一答。
     * AI 调用不包在事务里，落库交给 ChatMessageWriter（独立 Bean，@Transactional 才真正生效）。
     */
    public SendMessageResponse send(Long userId, Long conversationId, String userMessage) {
        Conversation conversation = loadOwned(userId, conversationId);
        AiTemplate template = templateService.loadUsable(userId, conversation.getTemplateId());

        List<DeepSeekClient.ChatTurn> history = loadHistory(conversationId);
        long aiStartedAt = System.currentTimeMillis();
        DeepSeekClient.AiResult result =
                deepSeekClient.chat(template.getContent(), history, userMessage);
        log.info("同步对话完成: conversationId={}, model={}, degraded={}, 上下文={}条, 耗时={}ms",
                conversationId, result.model(), result.degraded(), history.size(),
                System.currentTimeMillis() - aiStartedAt);

        return messageWriter.saveRound(conversationId, userMessage, result.content());
    }

    /**
     * 流式对话：通过 SSE 逐段推送，最后由独立的落库事务保存完整回复。
     * <p>调用方（Controller）负责创建 SseEmitter 并在异步线程里调用本方法。</p>
     */
    public void streamSend(Long userId, Long conversationId, String userMessage, SseEmitter emitter) {
        AtomicBoolean clientGone = new AtomicBoolean(false);
        long streamStartedAt = System.currentTimeMillis();

        try {
            Conversation conversation = loadOwned(userId, conversationId);
            AiTemplate template = templateService.loadUsable(userId, conversation.getTemplateId());
            List<DeepSeekClient.ChatTurn> history = loadHistory(conversationId);

            // 先落用户消息：即使后续流式失败，用户说的话也不会丢
            Message savedUser = messageWriter.saveUserMessage(conversationId, userMessage);
            sendEvent(emitter, clientGone, "user", toMessageVO(savedUser));

            DeepSeekClient.AiResult result = deepSeekClient.streamChat(
                    template.getContent(), history, userMessage,
                    delta -> sendEvent(emitter, clientGone, "delta", Map.of("text", delta)));

            log.info("流式对话完成: conversationId={}, model={}, degraded={}, 上下文={}条, 耗时={}ms",
                    conversationId, result.model(), result.degraded(), history.size(),
                    System.currentTimeMillis() - streamStartedAt);

            Message savedAssistant = messageWriter.saveAssistantMessage(conversationId, result.content());
            sendEvent(emitter, clientGone, "done", new SendMessageResponse(
                    toMessageVO(savedUser), toMessageVO(savedAssistant)));

        } catch (BusinessException e) {
            log.info("流式对话业务异常: conversationId={}, 耗时={}ms, {}",
                    conversationId, System.currentTimeMillis() - streamStartedAt, e.getMessage());
            sendEvent(emitter, clientGone, "error", Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("流式对话异常: conversationId={}", conversationId, e);
            sendEvent(emitter, clientGone, "error", Map.of("message", "AI 服务暂时不可用，请稍后重试"));
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 客户端已断开时会抛，忽略
            }
        }
    }

    @Transactional
    public void delete(Long userId, Long conversationId) {
        Conversation conversation = loadOwned(userId, conversationId);
        conversation.setDeleted(1);
        conversationRepository.save(conversation);
    }

    // ===================== 内部工具 =====================

    private void sendEvent(SseEmitter emitter, AtomicBoolean clientGone, String name, Object payload) {
        if (clientGone.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(payload, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // 客户端断开：标记后继续把模型输出读完并落库，避免丢消息
            clientGone.set(true);
            log.debug("SSE 推送失败（客户端可能已断开）: {}", e.getMessage());
        }
    }

    private List<DeepSeekClient.ChatTurn> loadHistory(Long conversationId) {
        List<Message> recent = messageRepository.findTop30ByConversationIdOrderByIdDesc(conversationId);
        if (recent.isEmpty()) {
            return List.of();
        }
        List<Message> ordered = new ArrayList<>(recent);
        Collections.reverse(ordered);

        // 从最近一条往前累加，直到超出字符预算或条数上限
        List<DeepSeekClient.ChatTurn> turns = new ArrayList<>();
        int used = 0;
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Message message = ordered.get(i);
            int cost = message.getContent() == null ? 0 : message.getContent().length();
            if (!turns.isEmpty() && (used + cost > CONTEXT_CHAR_BUDGET
                    || turns.size() >= CONTEXT_MESSAGE_LIMIT)) {
                break;
            }
            used += cost;
            turns.add(0, new DeepSeekClient.ChatTurn(message.getRole(), message.getContent()));
        }
        if (turns.size() < ordered.size()) {
            log.debug("上下文截断：共 {} 条，取最近 {} 条，约 {} 字符",
                    ordered.size(), turns.size(), used);
        }
        return turns;
    }

    private Conversation loadOwned(Long userId, Long conversationId) {
        return conversationRepository
                .findByIdAndUserIdAndDeleted(conversationId, userId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("会话不存在"));
    }

    private ConversationVO toVO(Conversation conversation, String templateName) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .templateId(conversation.getTemplateId())
                .templateName(templateName == null ? "已失效的角色" : templateName)
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private MessageVO toMessageVO(Message message) {
        return MessageVO.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 30;
        }
        return Math.min(size, 100);
    }
}
