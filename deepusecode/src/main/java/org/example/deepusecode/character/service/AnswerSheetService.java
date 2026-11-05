package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.dto.SaveSheetRequest;
import org.example.deepusecode.character.dto.SheetBriefVO;
import org.example.deepusecode.character.dto.SheetVO;
import org.example.deepusecode.character.entity.Answer;
import org.example.deepusecode.character.entity.AnswerSheet;
import org.example.deepusecode.character.entity.Question;
import org.example.deepusecode.character.entity.QuestionSet;
import org.example.deepusecode.character.repository.AnswerRepository;
import org.example.deepusecode.character.repository.AnswerSheetRepository;
import org.example.deepusecode.character.repository.QuestionRepository;
import org.example.deepusecode.character.repository.QuestionSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnswerSheetService {

    private final QuestionSetService questionSetService;
    private final QuestionSetRepository setRepository;
    private final QuestionRepository questionRepository;
    private final AnswerSheetRepository sheetRepository;
    private final AnswerRepository answerRepository;

    /** 某个题集下我的全部答卷（支持一人多份） */
    @Transactional(readOnly = true)
    public List<SheetBriefVO> listMine(Long userId, Long setId) {
        questionSetService.loadAnswerable(userId, setId);
        return sheetRepository.findBySetIdAndUserIdOrderByIdDesc(setId, userId).stream()
                .map(sheet -> SheetBriefVO.builder()
                        .id(sheet.getId())
                        .label(sheet.getLabel())
                        .personaName(sheet.getPersonaName())
                        .updatedAt(sheet.getUpdatedAt())
                        .answeredCount(answerRepository.countBySheetId(sheet.getId()))
                        .build())
                .toList();
    }

    /** 显式新建一份空白答卷 */
    @Transactional
    public SheetVO createSheet(Long userId, Long setId) {
        QuestionSet set = questionSetService.loadAnswerable(userId, setId);
        AnswerSheet sheet = new AnswerSheet();
        sheet.setSetId(setId);
        sheet.setUserId(userId);
        sheet.setLabel("版本 " + (sheetRepository.findBySetIdAndUserIdOrderByIdDesc(setId, userId).size() + 1));
        AnswerSheet saved = sheetRepository.save(sheet);

        // 用实际行数重算，而不是 +1 累加：累加一旦有任何漏加/重复就会永久漂移
        set.setAnswerCount((int) sheetRepository.countBySetId(setId));
        setRepository.save(set);

        return buildSheetVO(saved, set, questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId), List.of());
    }

    /**
     * 取答卷：指定 sheetId 就取那一份，否则取最新一份。
     * 一份都没有时返回一个 id 为 null 的“空白答卷”，前端可直接渲染填写页。
     */
    @Transactional(readOnly = true)
    public SheetVO get(Long userId, Long setId, Long sheetId) {
        QuestionSet set = questionSetService.loadAnswerable(userId, setId);
        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);

        AnswerSheet sheet = sheetId != null
                ? loadOwnedSheet(userId, setId, sheetId)
                : sheetRepository.findFirstBySetIdAndUserIdOrderByIdDesc(setId, userId).orElse(null);

        if (sheet == null) {
            return buildSheetVO(null, set, questions, List.of());
        }
        return buildSheetVO(sheet, set, questions, answerRepository.findBySheetId(sheet.getId()));
    }

    /**
     * 保存答卷。不传 sheetId 时复用最新一份，没有则新建，避免误点产生重复答卷。
     */
    @Transactional
    public SheetVO save(Long userId, Long setId, Long sheetId, SaveSheetRequest request) {
        QuestionSet set = questionSetService.loadAnswerable(userId, setId);
        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        Map<Long, Question> questionById = new HashMap<>();
        for (Question q : questions) {
            questionById.put(q.getId(), q);
        }

        AnswerSheet sheet;
        if (sheetId != null) {
            sheet = loadOwnedSheet(userId, setId, sheetId);
        } else {
            sheet = sheetRepository.findFirstBySetIdAndUserIdOrderByIdDesc(setId, userId)
                    .orElseGet(() -> {
                        AnswerSheet created = new AnswerSheet();
                        created.setSetId(setId);
                        created.setUserId(userId);
                        AnswerSheet saved = sheetRepository.save(created);
                        set.setAnswerCount((int) sheetRepository.countBySetId(setId));
                        setRepository.save(set);
                        return saved;
                    });
        }

        sheet.setPersonaName(trimToNull(request.getPersonaName()));
        sheet.setPersonaAge(request.getPersonaAge());
        sheet.setPersonaOccupation(trimToNull(request.getPersonaOccupation()));
        sheet.setNote(trimToNull(request.getNote()));
        if (request.getLabel() != null && !request.getLabel().isBlank()) {
            sheet.setLabel(request.getLabel().trim());
        }
        sheetRepository.save(sheet);

        answerRepository.deleteBySheetId(sheet.getId());
        List<Answer> toSave = new ArrayList<>();
        if (request.getAnswers() != null) {
            for (SaveSheetRequest.Item item : request.getAnswers()) {
                if (item.getQuestionId() == null) {
                    continue;
                }
                Question question = questionById.get(item.getQuestionId());
                if (question == null) {
                    throw BusinessException.badRequest("答案中的题目不属于该题集");
                }
                Answer answer = new Answer();
                answer.setSheetId(sheet.getId());
                answer.setQuestionId(item.getQuestionId());
                answer.setContent(item.getContent() == null ? null : item.getContent().trim());
                toSave.add(answer);
            }
        }
        answerRepository.saveAll(toSave);

        return buildSheetVO(sheet, set, questions, toSave);
    }

    private AnswerSheet loadOwnedSheet(Long userId, Long setId, Long sheetId) {
        AnswerSheet sheet = sheetRepository.findByIdAndUserId(sheetId, userId)
                .orElseThrow(() -> BusinessException.notFound("答卷不存在"));
        if (!sheet.getSetId().equals(setId)) {
            throw BusinessException.badRequest("该答卷不属于指定题集");
        }
        return sheet;
    }

    private SheetVO buildSheetVO(AnswerSheet sheet, QuestionSet set,
                                 List<Question> questions, List<Answer> answers) {
        Map<Long, String> answerByQuestion = new HashMap<>();
        for (Answer answer : answers) {
            answerByQuestion.put(answer.getQuestionId(), answer.getContent());
        }

        List<SheetVO.Item> items = questions.stream()
                .map(q -> SheetVO.Item.builder()
                        .questionId(q.getId())
                        .question(q.getContent())
                        .sortOrder(q.getSortOrder())
                        .content(answerByQuestion.get(q.getId()))
                        .build())
                .toList();

        return SheetVO.builder()
                .id(sheet == null ? null : sheet.getId())
                .setId(set.getId())
                .setTitle(set.getTitle())
                .personaName(sheet == null ? null : sheet.getPersonaName())
                .personaAge(sheet == null ? null : sheet.getPersonaAge())
                .personaOccupation(sheet == null ? null : sheet.getPersonaOccupation())
                .note(sheet == null ? null : sheet.getNote())
                .label(sheet == null ? null : sheet.getLabel())
                .createdAt(sheet == null ? null : sheet.getCreatedAt())
                .updatedAt(sheet == null ? null : sheet.getUpdatedAt())
                .answers(items)
                .build();
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
