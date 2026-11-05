package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Page<Conversation> findByUserIdAndDeletedOrderByUpdatedAtDesc(Long userId, Integer deleted, Pageable pageable);

    Optional<Conversation> findByIdAndUserIdAndDeleted(Long id, Long userId, Integer deleted);

    long countByUserIdAndDeleted(Long userId, Integer deleted);
}
