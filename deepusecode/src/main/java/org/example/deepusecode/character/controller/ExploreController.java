package org.example.deepusecode.character.controller;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.SetCardVO;
import org.example.deepusecode.character.dto.SetDetailVO;
import org.example.deepusecode.character.dto.TemplateCardVO;
import org.example.deepusecode.character.dto.TemplateVO;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.QuestionSetService;
import org.example.deepusecode.character.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 广场：模板与角色两类公开内容的只读浏览入口。
 * 全部 GET 且 permitAll，游客可看；登录后返回的 mine 字段依然正确。
 */
@RestController
@RequestMapping("/api/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final QuestionSetService questionSetService;
    private final TemplateService templateService;

    @GetMapping("/sets")
    public ApiResponse<PageResult<SetCardVO>> publicSets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(questionSetService.listPublic(keyword, sort, page, size));
    }

    @GetMapping("/sets/{id}")
    public ApiResponse<SetDetailVO> publicSetDetail(@PathVariable Long id) {
        return ApiResponse.success(questionSetService.publicDetail(CurrentUser.idOrNull(), id));
    }

    @GetMapping("/templates")
    public ApiResponse<PageResult<TemplateCardVO>> publicTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(templateService.listPublic(keyword, sort, page, size));
    }

    @GetMapping("/templates/{id}")
    public ApiResponse<TemplateVO> publicTemplateDetail(@PathVariable Long id) {
        return ApiResponse.success(templateService.detail(CurrentUser.idOrNull(), id));
    }
}
