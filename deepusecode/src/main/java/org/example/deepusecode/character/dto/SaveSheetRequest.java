package org.example.deepusecode.character.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 保存答卷。
 *
 * <p>结构对应前端的两个步骤：</p>
 * <ol>
 *   <li><b>基础信息</b>：{@code personaName}（昵称，内置唯一字段）+ {@code personaFields}（用户自定义类别）</li>
 *   <li><b>问答</b>：{@code answers}</li>
 * </ol>
 */
@Data
public class SaveSheetRequest {

    /** 角色昵称——内置的基本信息只有这一项 */
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String personaName;

    /** 用户自定义的基础信息类别，如 (职业, 程序员)、(年龄, 28) */
    @Valid
    @Size(max = 20, message = "基础信息类别最多 20 项")
    private List<PersonaFieldItem> personaFields;

    @Size(max = 1000, message = "补充说明不能超过 1000 个字符")
    private String note;

    /** 可选，用来区分同一题集下的多份答卷 */
    @Size(max = 60, message = "版本名不能超过 60 个字符")
    private String label;

    @Valid
    private List<Item> answers;

    @Data
    public static class PersonaFieldItem {
        @Size(max = 50, message = "类别名不能超过 50 个字符")
        private String label;

        @Size(max = 500, message = "内容不能超过 500 个字符")
        private String value;
    }

    @Data
    public static class Item {
        private Long questionId;

        @Size(max = 2000, message = "单个答案不能超过 2000 个字符")
        private String content;
    }
}
