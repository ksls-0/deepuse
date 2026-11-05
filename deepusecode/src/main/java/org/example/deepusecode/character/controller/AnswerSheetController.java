package org.example.deepusecode.character.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.dto.GenerateTemplateRequest;
import org.example.deepusecode.character.dto.SaveSheetRequest;
import org.example.deepusecode.character.dto.SheetBriefVO;
import org.example.deepusecode.character.dto.SheetVO;
import org.example.deepusecode.character.dto.TemplateVO;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.service.AnswerSheetService;
import org.example.deepusecode.character.service.TemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 答卷与“由答卷生成角色”。路径挂在模板下，语义更自然。
 */
@RestController
@RequestMapping("/api/sets/{setId}")
@RequiredArgsConstructor
public class AnswerSheetController {

    private final AnswerSheetService answerSheetService;
    private final TemplateService templateService;

    /** 我的全部答卷（一人可多份） */
    @GetMapping("/sheets")
    public ApiResponse<List<SheetBriefVO>> listSheets(@PathVariable Long setId) {
        return ApiResponse.success(answerSheetService.listMine(CurrentUser.id(), setId));
    }

    /** 新建一份空白答卷 */
    @PostMapping("/sheets")
    public ApiResponse<SheetVO> createSheet(@PathVariable Long setId) {
        return ApiResponse.success("已新建答卷", answerSheetService.createSheet(CurrentUser.id(), setId));
    }

    /**
     * 取答卷：带 sheetId 取指定版本，不带则取最新一份；
     * 一份都没有时返回 id 为 null 的空白结构，前端可直接渲染填写页。
     */
    @GetMapping("/sheet")
    public ApiResponse<SheetVO> getSheet(@PathVariable Long setId,
                                         @RequestParam(required = false) Long sheetId) {
        return ApiResponse.success(answerSheetService.get(CurrentUser.id(), setId, sheetId));
    }

    @PutMapping("/sheet")
    public ApiResponse<SheetVO> saveSheet(@PathVariable Long setId,
                                          @RequestParam(required = false) Long sheetId,
                                          @Valid @RequestBody SaveSheetRequest request) {
        return ApiResponse.success("已保存", answerSheetService.save(CurrentUser.id(), setId, sheetId, request));
    }

    /** 同步生成角色（可能耗时数十秒） */
    @PostMapping("/generate")
    public ApiResponse<TemplateVO> generate(@PathVariable Long setId,
                                            @RequestBody(required = false) GenerateTemplateRequest request) {
        Long sheetId = request == null ? null : request.getSheetId();
        return ApiResponse.success("生成成功",
                templateService.generate(CurrentUser.id(), setId, sheetId));
    }
}
