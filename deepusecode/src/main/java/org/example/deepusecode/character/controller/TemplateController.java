package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.PageResult;
import org.example.deepusecode.character.dto.TemplateCardVO;
import org.example.deepusecode.character.dto.TemplateVO;
import org.example.deepusecode.character.dto.UpdateTemplateRequest;
import org.example.deepusecode.character.dto.VisibilityRequest;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.TemplateService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping("/mine")
    public ApiResponse<PageResult<TemplateCardVO>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(templateService.listMine(CurrentUser.id(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<TemplateVO> detail(@PathVariable Long id) {
        return ApiResponse.success(templateService.detail(CurrentUser.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TemplateVO> update(@PathVariable Long id,
                                          @Valid @RequestBody UpdateTemplateRequest request) {
        return ApiResponse.success("已更新", templateService.update(CurrentUser.id(), id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<TemplateVO> visibility(@PathVariable Long id,
                                              @Valid @RequestBody VisibilityRequest request) {
        return ApiResponse.success(templateService.setVisibility(CurrentUser.id(), id, request.getVisibility()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(CurrentUser.id(), id);
        return ApiResponse.success("已删除", null);
    }
}
