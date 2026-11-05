package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.ConversationVO;
import org.example.deepusecode.character.dto.CreateConversationRequest;
import org.example.deepusecode.character.dto.MessageVO;
import org.example.deepusecode.character.dto.SendMessageRequest;
import org.example.deepusecode.character.dto.SendMessageResponse;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.ConversationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private static final long SSE_TIMEOUT_MS = 180_000L;

    private final ConversationService conversationService;
    private final ThreadPoolTaskExecutor chatStreamExecutor;

    /** 显式构造器 + @Qualifier：Lombok 的 @RequiredArgsConstructor 不会把 @Qualifier 带到构造参数上 */
    public ConversationController(ConversationService conversationService,
                                  @Qualifier("chatStreamExecutor") ThreadPoolTaskExecutor chatStreamExecutor) {
        this.conversationService = conversationService;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    @PostMapping
    public ApiResponse<ConversationVO> create(@Valid @RequestBody CreateConversationRequest request) {
        return ApiResponse.success("会话已创建",
                conversationService.create(CurrentUser.id(), request.getTemplateId()));
    }

    @GetMapping
    public ApiResponse<PageResult<ConversationVO>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(conversationService.listMine(CurrentUser.id(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<ConversationVO> detail(@PathVariable Long id) {
        return ApiResponse.success(conversationService.detail(CurrentUser.id(), id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<PageResult<MessageVO>> messages(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(
                conversationService.messages(CurrentUser.id(), id, beforeId, page, size));
    }

    /** 同步对话：一次性返回整段回复 */
    @PostMapping("/{id}/chat")
    public ApiResponse<SendMessageResponse> chat(@PathVariable Long id,
                                                 @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(
                conversationService.send(CurrentUser.id(), id, request.getMessage().trim()));
    }

    /**
     * 流式对话（SSE）。事件类型：
     * <ul>
     *   <li>{@code user}  — 用户消息已落库</li>
     *   <li>{@code delta} — 增量文本片段 {"text": "..."}</li>
     *   <li>{@code done}  — 结束，携带 userMessage / assistantMessage</li>
     *   <li>{@code error} — 出错 {"message": "..."}</li>
     * </ul>
     */
    @PostMapping(value = "/{id}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@PathVariable Long id,
                                 @Valid @RequestBody SendMessageRequest request) {
        // 必须在请求线程上解析当前用户，异步线程里没有 SecurityContext
        Long userId = CurrentUser.id();
        String message = request.getMessage().trim();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());

        chatStreamExecutor.execute(() ->
                conversationService.streamSend(userId, id, message, emitter));
        return emitter;
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        conversationService.delete(CurrentUser.id(), id);
        return ApiResponse.success("已删除", null);
    }
}
