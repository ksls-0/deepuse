package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.AccountMasker;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.CreateSetRequest;
import org.example.deepusecode.character.dto.QuestionPayload;
import org.example.deepusecode.character.dto.QuestionVO;
import org.example.deepusecode.character.dto.SetCardVO;
import org.example.deepusecode.character.dto.SetDetailVO;
import org.example.deepusecode.character.dto.UpdateSetRequest;
import org.example.deepusecode.character.entity.Question;
import org.example.deepusecode.character.entity.Moderation;
import org.example.deepusecode.character.entity.QuestionSet;
import org.example.deepusecode.character.entity.Visibility;
import org.example.deepusecode.character.repository.AnswerRepository;
import org.example.deepusecode.character.repository.QuestionRepository;
import org.example.deepusecode.character.repository.QuestionSetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionSetService {

    private static final int NOT_DELETED = 0;
    private static final int NOT_TAKEN_DOWN = Moderation.NOT_TAKEN_DOWN;

    private final QuestionSetRepository setRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final UserAccountResolver accountResolver;

    // ===================== 私有：我的模板 =====================

    @Transactional
    public SetDetailVO create(Long userId, CreateSetRequest request) {
        QuestionSet set = new QuestionSet();
        set.setOwnerId(userId);
        set.setTitle(request.getTitle().trim());
        set.setDescription(trimToNull(request.getDescription()));
        set.setVisibility(Visibility.PRIVATE);
        set.setQuestionCount(0);
        set.setAnswerCount(0);
        set.setImportCount(0);
        set.setDeleted(NOT_DELETED);
        QuestionSet saved = setRepository.save(set);

        List<Question> questions = syncQuestions(saved.getId(), request.getQuestions());
        saved.setQuestionCount(questions.size());
        setRepository.save(saved);

        return toDetail(saved, questions, accountResolver.resolveOne(userId), userId);
    }

    @Transactional(readOnly = true)
    public PageResult<SetCardVO> listMine(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<QuestionSet> result = setRepository.findByOwnerIdAndDeleted(userId, NOT_DELETED, pageable);
        String account = accountResolver.resolveOne(userId);
        return PageResult.of(result, set -> toCard(set, account, userId));
    }

    @Transactional(readOnly = true)
    public SetDetailVO detail(Long userId, Long setId) {
        QuestionSet set = loadOwned(userId, setId);
        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        return toDetail(set, questions, accountResolver.resolveOne(userId), userId);
    }

    @Transactional
    public SetDetailVO update(Long userId, Long setId, UpdateSetRequest request) {
        QuestionSet set = loadOwned(userId, setId);
        set.setTitle(request.getTitle().trim());
        set.setDescription(trimToNull(request.getDescription()));

        // 关键：按 id 增量同步，保住既有 question.id，
        // 否则已保存的答卷里的 question_id 会全部失效（页面表现为"答案全空"）。
        List<Question> questions = syncQuestions(setId, request.getQuestions());
        set.setQuestionCount(questions.size());
        setRepository.save(set);

        return toDetail(set, questions, accountResolver.resolveOne(userId), userId);
    }

    @Transactional
    public void delete(Long userId, Long setId) {
        QuestionSet set = loadOwned(userId, setId);
        set.setDeleted(1);
        setRepository.save(set);
    }

    @Transactional
    public SetDetailVO setVisibility(Long userId, Long setId, Integer visibility) {
        QuestionSet set = loadOwned(userId, setId);
        set.setVisibility(Visibility.normalize(visibility));
        setRepository.save(set);
        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        return toDetail(set, questions, accountResolver.resolveOne(userId), userId);
    }

    /**
     * 一键导入：把公开模板深拷贝一份到当前用户名下。
     */
    @Transactional
    public SetDetailVO importSet(Long userId, Long publicSetId) {
        QuestionSet source = setRepository.findByIdAndDeleted(publicSetId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在或已下架"));

        if (!Visibility.isPublic(source.getVisibility())) {
            throw BusinessException.forbidden("该模板未公开，无法导入");
        }
        if (source.getOwnerId().equals(userId)) {
            throw BusinessException.conflict("不能导入自己创建的模板");
        }

        List<Question> sourceQuestions =
                questionRepository.findBySetIdOrderBySortOrderAscIdAsc(publicSetId);

        QuestionSet copy = new QuestionSet();
        copy.setOwnerId(userId);
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setVisibility(Visibility.PRIVATE);
        copy.setQuestionCount(sourceQuestions.size());
        copy.setAnswerCount(0);
        copy.setImportCount(0);
        copy.setDeleted(NOT_DELETED);
        QuestionSet savedCopy = setRepository.save(copy);

        List<Question> copiedQuestions = new ArrayList<>(sourceQuestions.size());
        int order = 0;
        for (Question original : sourceQuestions) {
            Question q = new Question();
            q.setSetId(savedCopy.getId());
            q.setContent(original.getContent());
            q.setSortOrder(order++);
            copiedQuestions.add(q);
        }
        questionRepository.saveAll(copiedQuestions);

        source.setImportCount(source.getImportCount() == null ? 1 : source.getImportCount() + 1);
        setRepository.save(source);

        return toDetail(savedCopy, copiedQuestions, accountResolver.resolveOne(userId), userId);
    }

    // ===================== 公开：广场 =====================

    @Transactional(readOnly = true)
    public PageResult<SetCardVO> listPublic(String keyword, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), publicSort(sort));
        String kw = keyword == null ? "" : keyword.trim();

        Page<QuestionSet> result = kw.isEmpty()
                ? setRepository.findByVisibilityAndDeletedAndTakenDown(
                        Visibility.PUBLIC, NOT_DELETED, NOT_TAKEN_DOWN, pageable)
                : setRepository.findByVisibilityAndDeletedAndTakenDownAndTitleContaining(
                        Visibility.PUBLIC, NOT_DELETED, NOT_TAKEN_DOWN, kw, pageable);

        Map<Long, String> accounts =
                accountResolver.resolve(result.getContent().stream().map(QuestionSet::getOwnerId).toList());
        return PageResult.of(result, set -> toCard(set, accounts.get(set.getOwnerId()), null));
    }

    @Transactional(readOnly = true)
    public SetDetailVO publicDetail(Long viewerId, Long setId) {
        QuestionSet set = setRepository.findByIdAndDeleted(setId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在或已下架"));

        boolean isOwner = viewerId != null && viewerId.equals(set.getOwnerId());
        if (Moderation.isTakenDown(set.getTakenDown()) && !isOwner) {
            throw BusinessException.notFound("该模板已被下架");
        }
        if (!Visibility.isPublic(set.getVisibility()) && !isOwner) {
            throw BusinessException.notFound("模板不存在或未公开");
        }

        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        return toDetail(set, questions, accountResolver.resolveOne(set.getOwnerId()), viewerId);
    }

    // ===================== 内部工具 =====================

    public QuestionSet loadAnswerable(Long viewerId, Long setId) {
        QuestionSet set = setRepository.findByIdAndDeleted(setId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在或已下架"));
        boolean isOwner = viewerId.equals(set.getOwnerId());
        if (!isOwner && Moderation.isTakenDown(set.getTakenDown())) {
            throw BusinessException.notFound("该模板已被下架");
        }
        if (!isOwner && !Visibility.isPublic(set.getVisibility())) {
            throw BusinessException.notFound("模板不存在或未公开");
        }
        return set;
    }

    private QuestionSet loadOwned(Long userId, Long setId) {
        return setRepository.findByIdAndOwnerIdAndDeleted(setId, userId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在"));
    }

    /**
     * 按 id 增量同步题目：保住既有 id，只增删改差异部分。
     */
    private List<Question> syncQuestions(Long setId, List<QuestionPayload> payloads) {
        List<Question> existing = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        Map<Long, Question> existingById = existing.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        List<Question> result = new ArrayList<>();
        Set<Long> keptIds = new HashSet<>();
        int order = 0;

        if (payloads != null) {
            for (QuestionPayload payload : payloads) {
                if (payload == null || payload.getContent() == null || payload.getContent().isBlank()) {
                    continue;
                }
                Question question = payload.getId() == null ? null : existingById.get(payload.getId());
                if (question == null) {
                    question = new Question();
                    question.setSetId(setId);
                } else {
                    keptIds.add(question.getId());
                }
                question.setContent(payload.getContent().trim());
                question.setSortOrder(order++);
                result.add(question);
            }
        }

        List<Long> removedIds = existing.stream()
                .map(Question::getId)
                .filter(id -> !keptIds.contains(id))
                .toList();
        if (!removedIds.isEmpty()) {
            // 题目被删掉时，连带清理指向它的答案，避免留下孤儿数据
            answerRepository.deleteByQuestionIdIn(removedIds);
            questionRepository.deleteAllById(removedIds);
        }

        return questionRepository.saveAll(result);
    }

    private SetCardVO toCard(QuestionSet set, String ownerAccount, Long viewerId) {
        return SetCardVO.builder()
                .id(set.getId())
                .title(set.getTitle())
                .description(set.getDescription())
                .visibility(set.getVisibility())
                .questionCount(set.getQuestionCount())
                .answerCount(set.getAnswerCount())
                .importCount(set.getImportCount())
                .ownerAccount(AccountMasker.maskForViewer(ownerAccount, viewerId, set.getOwnerId()))
                .createdAt(set.getCreatedAt())
                .updatedAt(set.getUpdatedAt())
                .mine(viewerId != null && viewerId.equals(set.getOwnerId()))
                .takenDown(Moderation.isTakenDown(set.getTakenDown()))
                .takedownReason(set.getTakedownReason())
                .build();
    }

    private SetDetailVO toDetail(QuestionSet set, List<Question> questions,
                                 String ownerAccount, Long viewerId) {
        List<QuestionVO> questionVOs = questions.stream()
                .map(q -> new QuestionVO(q.getId(), q.getContent(), q.getSortOrder()))
                .toList();
        return SetDetailVO.builder()
                .id(set.getId())
                .title(set.getTitle())
                .description(set.getDescription())
                .visibility(set.getVisibility())
                .questionCount(set.getQuestionCount())
                .answerCount(set.getAnswerCount())
                .importCount(set.getImportCount())
                .ownerId(set.getOwnerId())
                .ownerAccount(AccountMasker.maskForViewer(ownerAccount, viewerId, set.getOwnerId()))
                .createdAt(set.getCreatedAt())
                .updatedAt(set.getUpdatedAt())
                .mine(viewerId != null && viewerId.equals(set.getOwnerId()))
                .takenDown(Moderation.isTakenDown(set.getTakenDown()))
                .takedownReason(set.getTakedownReason())
                .takenDownAt(set.getTakenDownAt())
                .questions(questionVOs)
                .build();
    }

    private Sort publicSort(String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "importCount")
                    .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 12;
        }
        return Math.min(size, 50);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
