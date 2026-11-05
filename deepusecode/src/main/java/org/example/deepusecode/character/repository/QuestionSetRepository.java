package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.QuestionSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {

    Optional<QuestionSet> findByIdAndDeleted(Long id, Integer deleted);

    Optional<QuestionSet> findByIdAndOwnerIdAndDeleted(Long id, Long ownerId, Integer deleted);

    Page<QuestionSet> findByOwnerIdAndDeleted(Long ownerId, Integer deleted, Pageable pageable);

    // ===== 广场：必须同时满足「已公开 + 未下架 + 未删除」 =====

    Page<QuestionSet> findByVisibilityAndDeletedAndTakenDown(
            Integer visibility, Integer deleted, Integer takenDown, Pageable pageable);

    Page<QuestionSet> findByVisibilityAndDeletedAndTakenDownAndTitleContaining(
            Integer visibility, Integer deleted, Integer takenDown, String keyword, Pageable pageable);

    long countByOwnerIdAndDeleted(Long ownerId, Integer deleted);

    long countByTakenDownAndDeleted(Integer takenDown, Integer deleted);
}
