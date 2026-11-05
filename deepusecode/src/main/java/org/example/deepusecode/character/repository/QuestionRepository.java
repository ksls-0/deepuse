package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findBySetIdOrderBySortOrderAscIdAsc(Long setId);

    void deleteBySetId(Long setId);

    long countBySetId(Long setId);
}
