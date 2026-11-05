package org.example.deepusecode.character.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserVO {
    private Long id;
    private String account;
    private LocalDateTime createTime;
}
