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

    Page<QuestionSet> findByVisibilityAndDeleted(Integer visibility, Integer deleted, Pageable pageable);

    Page<QuestionSet> findByVisibilityAndDeletedAndTitleContaining(
            Integer visibility, Integer deleted, String keyword, Pageable pageable);

    long countByOwnerIdAndDeleted(Long ownerId, Integer deleted);
}
