package org.example.deepusecode.character.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private Long userId;
    private String account;
    /** USER / ADMIN，前端据此决定是否显示管理入口 */
    private String role;
}
