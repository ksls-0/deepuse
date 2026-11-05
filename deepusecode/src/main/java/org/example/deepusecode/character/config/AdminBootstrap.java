package org.example.deepusecode.character.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.deepusecode.character.entity.Role;
import org.example.deepusecode.character.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 启动时把白名单里的账号提升为管理员。
 *
 * <p>覆盖「账号先于配置存在」的情况；注册时 {@code UserService} 也会查白名单，
 * 覆盖「配置先于账号存在」的情况。两边都不需要手工改库。</p>
 *
 * <p>注意：角色写在 JWT 的 claim 里，所以角色变更后需要重新登录才会生效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<String> accounts = adminProperties.getAccounts();
        if (accounts == null || accounts.isEmpty()) {
            log.warn("未配置 app.admin.accounts，本次启动没有任何管理员账号，内容审核接口将无人可用");
            return;
        }

        for (String account : accounts) {
            if (account == null || account.isBlank()) {
                continue;
            }
            String target = account.trim();
            userRepository.findByAccount(target).ifPresentOrElse(user -> {
                if (!Role.isAdmin(user.getRole())) {
                    user.setRole(Role.ADMIN);
                    userRepository.save(user);
                    log.info("已将账号提升为管理员: {}", target);
                }
            }, () -> log.info("管理员账号尚未注册，注册后会自动成为管理员: {}", target));
        }
    }
}
