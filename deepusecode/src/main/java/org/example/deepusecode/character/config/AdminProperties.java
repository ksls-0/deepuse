package org.example.deepusecode.character.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员账号白名单。
 *
 * <p>用法：在配置里写 {@code app.admin.accounts=admin@example.com,13800138000}，
 * 这些账号注册时会直接被设为 ADMIN，已存在的账号在启动时被提升为 ADMIN。</p>
 *
 * <p>这样做的好处是不需要在数据库里手工改 role，也不需要提供默认管理员密码。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.admin")
public class AdminProperties {

    /** 允许成为管理员的账号（邮箱或手机号） */
    private List<String> accounts = new ArrayList<>();

    public boolean isConfiguredAdmin(String account) {
        if (account == null || account.isBlank() || accounts == null) {
            return false;
        }
        String target = account.trim();
        return accounts.stream()
                .filter(a -> a != null && !a.isBlank())
                .anyMatch(a -> a.trim().equalsIgnoreCase(target));
    }
}
