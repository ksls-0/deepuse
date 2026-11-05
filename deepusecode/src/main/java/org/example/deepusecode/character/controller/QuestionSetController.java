package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.CreateSetRequest;
import org.example.deepusecode.character.dto.SetCardVO;
import org.example.deepusecode.character.dto.SetDetailVO;
import org.example.deepusecode.character.dto.UpdateSetRequest;
import org.example.deepusecode.character.dto.VisibilityRequest;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.QuestionSetService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sets")
@RequiredArgsConstructor
public class QuestionSetController {

    private final QuestionSetService questionSetService;

    @PostMapping
    public ApiResponse<SetDetailVO> create(@Valid @RequestBody CreateSetRequest request) {
        return ApiResponse.success(questionSetService.create(CurrentUser.id(), request));
    }

    @GetMapping("/mine")
    public ApiResponse<PageResult<SetCardVO>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(questionSetService.listMine(CurrentUser.id(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<SetDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(questionSetService.detail(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<SetDetailVO> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateSetRequest request) {
        return ApiResponse.success(questionSetService.update(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionSetService.delete(CurrentUser.id(), id);
        return ApiResponse.success("已删除", null);
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<SetDetailVO> visibility(@PathVariable Long id,
                                               @Valid @RequestBody VisibilityRequest request) {
        return ApiResponse.success(
                questionSetService.setVisibility(CurrentUser.id(), id, request.getVisibility()));
    }

    /**
     * 一键导入公开模板到我名下（走登录态，所以放在 /api/sets 下而不是 /api/explore）。
     */
    @PostMapping("/import/{publicSetId}")
    public ApiResponse<SetDetailVO> importSet(@PathVariable Long publicSetId) {
        return ApiResponse.success(
                "导入成功", questionSetService.importSet(CurrentUser.id(), publicSetId));
    }
}
