package org.example.deepusecode.character.repository;

import org.example.deepusecode.character.entity.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AnswerRepository extends JpaRepository<Answer, Long> {

    List<Answer> findBySheetId(Long sheetId);

    void deleteBySheetId(Long sheetId);

    /** 题目被删掉时，连带清理指向它的答案，避免留下孤儿数据 */
    void deleteByQuestionIdIn(Collection<Long> questionIds);

    long countBySheetId(Long sheetId);
}
