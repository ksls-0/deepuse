package org.example.deepusecode.character.service;

import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.entity.User;
import org.example.deepusecode.character.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String account, String rawPassword) {
        if (userRepository.existsByAccount(account)) {
            throw BusinessException.conflict("该手机号/邮箱已注册");
        }
        User user = new User();
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (account.matches(PHONE_REGEX)) {
            user.setPhone(account);
        } else {
            user.setEmail(account);
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User login(String account, String rawPassword) {
        User user = userRepository.findByAccount(account)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "账号或密码错误"));
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
