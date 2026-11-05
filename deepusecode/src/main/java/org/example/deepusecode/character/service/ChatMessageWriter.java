package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.dto.MessageVO;
import org.example.deepusecode.character.dto.SendMessageResponse;
import org.example.deepusecode.character.entity.Message;
import org.example.deepusecode.character.repository.ConversationRepository;
import org.example.deepusecode.character.repository.MessageRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息落库。
 * <p>单独抽成 Bean 而不是留在 ConversationService 里，是因为 @Transactional
 * 走的是 Spring 代理：同一个类里的自调用不会开事务，会导致「用户消息存进去了、
 * AI 回复没存进去」的半轮数据。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    /** 同步对话：一问一答 + 刷新会话时间，放在同一个事务里 */
    @Transactional
    public SendMessageResponse saveRound(Long conversationId, String userMessage, String reply) {
        Message user = saveUserMessage(conversationId, userMessage);
        Message assistant = saveAssistantMessage(conversationId, reply);
        touch(conversationId);
        return new SendMessageResponse(toVO(user), toVO(assistant));
    }

    /** 流式对话：先把用户消息落库，避免长时间流式期间界面刷新丢消息 */
    @Transactional
    public Message saveUserMessage(Long conversationId, String text) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(Message.ROLE_USER);
        message.setContent(text);
        return messageRepository.save(message);
    }

    /** 流式对话结束：保存完整回复并刷新会话时间 */
    @Transactional
    public Message saveAssistantMessage(Long conversationId, String reply) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(Message.ROLE_ASSISTANT);
        message.setContent(reply);
        Message saved = messageRepository.save(message);
        touch(conversationId);
        return saved;
    }

    private void touch(Long conversationId) {
        conversationRepository.findById(conversationId).ifPresent(c -> {
            c.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(c);
        });
    }

    private MessageVO toVO(Message message) {
        return MessageVO.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
