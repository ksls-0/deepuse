package org.example.deepusecode.character.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.ai.CardJsonParser;
import org.example.deepusecode.character.ai.CharacterPromptContext;
import org.example.deepusecode.character.ai.DeepSeekClient;
import org.example.deepusecode.character.ai.PromptBuilder;
import org.example.deepusecode.character.common.AccountMasker;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.TemplateCardVO;
import org.example.deepusecode.character.dto.TemplateVO;
import org.example.deepusecode.character.dto.UpdateTemplateRequest;
import org.example.deepusecode.character.entity.AiTemplate;
import org.example.deepusecode.character.entity.Answer;
import org.example.deepusecode.character.entity.AnswerSheet;
import org.example.deepusecode.character.entity.GenStatus;
import org.example.deepusecode.character.entity.Question;
import org.example.deepusecode.character.entity.QuestionSet;
import org.example.deepusecode.character.entity.Visibility;
import org.example.deepusecode.character.repository.AiTemplateRepository;
import org.example.deepusecode.character.repository.AnswerRepository;
import org.example.deepusecode.character.repository.AnswerSheetRepository;
import org.example.deepusecode.character.repository.QuestionRepository;
import org.example.deepusecode.character.repository.QuestionSetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private static final int NOT_DELETED = 0;

    private final AiTemplateRepository templateRepository;
    private final QuestionSetRepository setRepository;
    private final QuestionRepository questionRepository;
    private final AnswerSheetRepository sheetRepository;
    private final AnswerRepository answerRepository;
    private final QuestionSetService questionSetService;
    private final UserAccountResolver accountResolver;
    private final PromptBuilder promptBuilder;
    private final DeepSeekClient deepSeekClient;
    private final CardJsonParser cardJsonParser;

    // ===================== 生成 =====================

    /**
     * 同步生成角色模板。
     * <p>刻意不在方法上开事务：AI 调用可能耗时数十秒，
     * 若包在事务里会把数据库连接一直占着。读取各自短事务，落库时再开一个。</p>
     */
    public TemplateVO generate(Long userId, Long setId, Long sheetId) {
        QuestionSet set = questionSetService.loadAnswerable(userId, setId);

        AnswerSheet sheet = (sheetId != null
                ? sheetRepository.findByIdAndUserId(sheetId, userId)
                : sheetRepository.findFirstBySetIdAndUserIdOrderByIdDesc(setId, userId))
                .orElseThrow(() -> BusinessException.badRequest("请先填写答卷，再生成模板"));

        List<Question> questions = questionRepository.findBySetIdOrderBySortOrderAscIdAsc(setId);
        List<Answer> answers = answerRepository.findBySheetId(sheet.getId());

        CharacterPromptContext ctx = buildContext(set, sheet, questions, answers);
        if (ctx.answeredCount() == 0) {
            throw BusinessException.badRequest("请至少回答一个问题再生成模板");
        }

        String prompt = promptBuilder.buildCharacterPrompt(ctx);
        long aiStartedAt = System.currentTimeMillis();
        DeepSeekClient.AiResult aiResult = deepSeekClient.generateCharacterCard(prompt, ctx);
        log.info("模板生成完成: ownerId={}, setId={}, model={}, degraded={}, 耗时={}ms",
                userId, setId, aiResult.model(), aiResult.degraded(),
                System.currentTimeMillis() - aiStartedAt);

        return persist(userId, set, sheet, ctx, aiResult);
    }

    /**
     * 落库。不加 @Transactional：一是它在同类内部被调用（自调用不会经过代理），
     * 二是这里只有一次 save，JpaRepository 自身已带事务。
     */
    private TemplateVO persist(Long userId, QuestionSet set, AnswerSheet sheet,
                               CharacterPromptContext ctx, DeepSeekClient.AiResult aiResult) {
        JsonNode card = cardJsonParser.parse(aiResult.content());

        String systemPrompt = aiResult.content();
        String name = ctx.displayName();
        String rawJson = null;

        if (card != null) {
            rawJson = card.toString();
            if (card.hasNonNull("systemPrompt") && !card.get("systemPrompt").asText().isBlank()) {
                systemPrompt = card.get("systemPrompt").asText();
            }
            if (card.hasNonNull("name") && !card.get("name").asText().isBlank()) {
                name = card.get("name").asText();
            }
        }

        AiTemplate template = new AiTemplate();
        template.setOwnerId(userId);
        template.setSetId(set.getId());
        template.setSheetId(sheet.getId());
        template.setName(name.length() > 80 ? name.substring(0, 80) : name);
        template.setContent(systemPrompt);
        template.setRawJson(rawJson);
        template.setVisibility(Visibility.PRIVATE);
        template.setGenStatus(GenStatus.SUCCESS);
        template.setGenModel(aiResult.model());
        template.setUseCount(0);
        template.setDeleted(NOT_DELETED);
        if (card == null) {
            template.setGenError("模型未返回可解析的 JSON，已按纯文本保存");
        }

        AiTemplate saved = templateRepository.save(template);
        log.info("模板已生成, id={}, ownerId={}, model={}, structured={}",
                saved.getId(), userId, aiResult.model(), card != null);

        return toVO(saved, set, userId, accountResolver.resolveOne(userId));
    }

    private CharacterPromptContext buildContext(QuestionSet set, AnswerSheet sheet,
                                                List<Question> questions, List<Answer> answers) {
        Map<Long, String> answerByQuestion = new HashMap<>();
        for (Answer answer : answers) {
            answerByQuestion.put(answer.getQuestionId(), answer.getContent());
        }

        List<CharacterPromptContext.Qa> qa = questions.stream()
                .map(q -> new CharacterPromptContext.Qa(q.getContent(), answerByQuestion.get(q.getId())))
                .toList();

        return new CharacterPromptContext(
                set.getTitle(),
                set.getDescription(),
                sheet.getPersonaName(),
                sheet.getPersonaAge(),
                sheet.getPersonaOccupation(),
                sheet.getNote(),
                qa
        );
    }

    // ===================== 私有模板 =====================

    @Transactional(readOnly = true)
    public PageResult<TemplateCardVO> listMine(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AiTemplate> result = templateRepository.findByOwnerIdAndDeleted(userId, NOT_DELETED, pageable);
        Map<Long, String> accounts = accountResolver.resolve(List.of(userId));
        return PageResult.of(result, t -> toCard(t, accounts, userId));
    }

    @Transactional(readOnly = true)
    public TemplateVO detail(Long viewerId, Long templateId) {
        AiTemplate template = loadVisible(viewerId, templateId);
        QuestionSet set = template.getSetId() == null ? null
                : setRepository.findById(template.getSetId()).orElse(null);
        return toVO(template, set, viewerId, accountResolver.resolveOne(template.getOwnerId()));
    }

    @Transactional
    public TemplateVO update(Long userId, Long templateId, UpdateTemplateRequest request) {
        AiTemplate template = loadOwned(userId, templateId);
        template.setName(request.getName().trim());
        template.setContent(request.getContent());
        AiTemplate saved = templateRepository.save(template);
        QuestionSet set = saved.getSetId() == null ? null
                : setRepository.findById(saved.getSetId()).orElse(null);
        return toVO(saved, set, userId, accountResolver.resolveOne(userId));
    }

    @Transactional
    public void delete(Long userId, Long templateId) {
        AiTemplate template = loadOwned(userId, templateId);
        template.setDeleted(1);
        templateRepository.save(template);
    }

    @Transactional
    public TemplateVO setVisibility(Long userId, Long templateId, Integer visibility) {
        AiTemplate template = loadOwned(userId, templateId);
        template.setVisibility(Visibility.normalize(visibility));
        AiTemplate saved = templateRepository.save(template);
        QuestionSet set = saved.getSetId() == null ? null
                : setRepository.findById(saved.getSetId()).orElse(null);
        return toVO(saved, set, userId, accountResolver.resolveOne(userId));
    }

    // ===================== 公开模板 =====================

    @Transactional(readOnly = true)
    public PageResult<TemplateCardVO> listPublic(String keyword, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), publicSort(sort));
        String kw = keyword == null ? "" : keyword.trim();

        Page<AiTemplate> result = kw.isEmpty()
                ? templateRepository.findByVisibilityAndDeleted(Visibility.PUBLIC, NOT_DELETED, pageable)
                : templateRepository.findByVisibilityAndDeletedAndNameContaining(
                        Visibility.PUBLIC, NOT_DELETED, kw, pageable);

        Map<Long, String> accounts = accountResolver.resolve(
                result.getContent().stream().map(AiTemplate::getOwnerId).toList());
        return PageResult.of(result, t -> toCard(t, accounts, null));
    }

    /**
     * 供对话使用：模板必须是自己的，或者是公开的。
     */
    @Transactional(readOnly = true)
    public AiTemplate loadUsable(Long userId, Long templateId) {
        return loadVisible(userId, templateId);
    }

    /**
     * 批量取模板名，避免会话列表出现 N+1 查询。
     * 不可见（已删 / 已转私密）的模板不会出现在返回结果里，调用方按"已失效"处理。
     */
    @Transactional(readOnly = true)
    public Map<Long, String> templateNames(Long userId, List<Long> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (AiTemplate template : templateRepository.findAllById(templateIds)) {
            if (template.getDeleted() != null && template.getDeleted() != NOT_DELETED) {
                continue;
            }
            boolean isOwner = userId != null && userId.equals(template.getOwnerId());
            if (isOwner || Visibility.isPublic(template.getVisibility())) {
                names.put(template.getId(), template.getName());
            }
        }
        return names;
    }

    @Transactional
    public void increaseUseCount(Long templateId) {
        templateRepository.findById(templateId).ifPresent(t -> {
            t.setUseCount((t.getUseCount() == null ? 0 : t.getUseCount()) + 1);
            templateRepository.save(t);
        });
    }

    // ===================== 内部工具 =====================

    private AiTemplate loadOwned(Long userId, Long templateId) {
        return templateRepository.findByIdAndOwnerIdAndDeleted(templateId, userId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在"));
    }

    private AiTemplate loadVisible(Long viewerId, Long templateId) {
        AiTemplate template = templateRepository.findByIdAndDeleted(templateId, NOT_DELETED)
                .orElseThrow(() -> BusinessException.notFound("模板不存在或已下架"));

        boolean isOwner = viewerId != null && viewerId.equals(template.getOwnerId());
        if (!isOwner && !Visibility.isPublic(template.getVisibility())) {
            throw BusinessException.notFound("模板不存在或未公开");
        }
        return template;
    }

    private TemplateVO toVO(AiTemplate template, QuestionSet set, Long viewerId, String ownerAccount) {
        Object structured = null;
        if (template.getRawJson() != null && !template.getRawJson().isBlank()) {
            JsonNode node = cardJsonParser.parse(template.getRawJson());
            structured = node;
        }

        return TemplateVO.builder()
                .id(template.getId())
                .name(template.getName())
                .content(template.getContent())
                .structured(structured)
                .visibility(template.getVisibility())
                .genStatus(template.getGenStatus())
                .genModel(template.getGenModel())
                .useCount(template.getUseCount())
                .setId(template.getSetId())
                .setTitle(set == null ? null : set.getTitle())
                .ownerId(template.getOwnerId())
                .ownerAccount(AccountMasker.maskForViewer(
                        ownerAccount, viewerId, template.getOwnerId()))
                .createdAt(template.getCreatedAt())
                .mine(viewerId != null && viewerId.equals(template.getOwnerId()))
                .build();
    }

    private TemplateCardVO toCard(AiTemplate template, Map<Long, String> accounts, Long viewerId) {
        return TemplateCardVO.builder()
                .id(template.getId())
                .name(template.getName())
                .tagline(extractTagline(template.getRawJson()))
                .visibility(template.getVisibility())
                .ownerAccount(AccountMasker.maskForViewer(
                        accounts.get(template.getOwnerId()), viewerId, template.getOwnerId()))
                .useCount(template.getUseCount())
                .setId(template.getSetId())
                .createdAt(template.getCreatedAt())
                .mine(viewerId != null && viewerId.equals(template.getOwnerId()))
                .build();
    }

    private String extractTagline(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        JsonNode node = cardJsonParser.parse(rawJson);
        if (node == null || !node.hasNonNull("tagline")) {
            return "";
        }
        return node.get("tagline").asText("");
    }

    private Sort publicSort(String sort) {
        if ("hot".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "useCount")
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
}
