package org.example.deepusecode.character.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class SaveSheetRequest {

    @Size(max = 50, message = "角色名不能超过 50 个字符")
    private String personaName;

    @Min(value = 0, message = "年龄不能为负数")
    @Max(value = 300, message = "年龄不合理")
    private Integer personaAge;

    @Size(max = 50, message = "职业不能超过 50 个字符")
    private String personaOccupation;

    @Size(max = 1000, message = "补充说明不能超过 1000 个字符")
    private String note;

    /** 可选，用来区分同一题集下的多份答卷 */
    @Size(max = 60, message = "版本名不能超过 60 个字符")
    private String label;

    @Valid
    private List<Item> answers;

    @Data
    public static class Item {
        private Long questionId;

        @Size(max = 2000, message = "单个答案不能超过 2000 个字符")
        private String content;
    }
}
