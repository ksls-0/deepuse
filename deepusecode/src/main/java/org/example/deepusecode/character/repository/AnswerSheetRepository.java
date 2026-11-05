package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.AnswerSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnswerSheetRepository extends JpaRepository<AnswerSheet, Long> {

    /** 一人一模板可有多份，按新的在前排 */
    List<AnswerSheet> findBySetIdAndUserIdOrderByIdDesc(Long setId, Long userId);

    Optional<AnswerSheet> findFirstBySetIdAndUserIdOrderByIdDesc(Long setId, Long userId);

    Optional<AnswerSheet> findByIdAndUserId(Long id, Long userId);

    long countBySetId(Long setId);

    long countByUserId(Long userId);
}
