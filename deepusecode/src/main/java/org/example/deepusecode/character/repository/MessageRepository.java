package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByIdAsc(Long conversationId, Pageable pageable);

    Page<Message> findByConversationIdAndIdLessThanOrderByIdDesc(
            Long conversationId, Long beforeId, Pageable pageable);

    /** 取最近 N 条作为对话上下文，返回顺序是倒序，调用方需反转 */
    List<Message> findTop30ByConversationIdOrderByIdDesc(Long conversationId);

    long countByConversationId(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
