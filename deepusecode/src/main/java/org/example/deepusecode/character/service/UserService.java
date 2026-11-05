package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.entity.User;
import org.example.deepusecode.character.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户注册与登录。邮箱与手机号是两条独立通道，各自唯一。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ===================== 注册 =====================

    @Transactional
    public User registerByEmail(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw BusinessException.conflict("该邮箱已注册");
        }
        User user = new User();
        user.setEmail(email);
        user.setAccount(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return saveSafely(user, "该邮箱已注册");
    }

    @Transactional
    public User registerByPhone(String phone, String rawPassword) {
        if (userRepository.existsByPhone(phone)) {
            throw BusinessException.conflict("该手机号已注册");
        }
        User user = new User();
        user.setPhone(phone);
        user.setAccount(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return saveSafely(user, "该手机号已注册");
    }

    /**
     * 并发下 existsBy 检查可能被绕过，靠唯一索引兜底，
     * 把约束冲突翻译成可读的业务错误。
     */
    private User saveSafely(User user, String conflictMessage) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict(conflictMessage);
        }
    }

    // ===================== 登录 =====================

    @Transactional(readOnly = true)
    public User loginByEmail(String email, String rawPassword) {
        return loginWith(userRepository.findByEmail(email), rawPassword);
    }

    @Transactional(readOnly = true)
    public User loginByPhone(String phone, String rawPassword) {
        return loginWith(userRepository.findByPhone(phone), rawPassword);
    }

    private User loginWith(Optional<User> found, String rawPassword) {
        User user = found.orElseThrow(
                () -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误"));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
    }
}
