package org.example.deepusecode.character.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.config.JwtUtil;
import org.example.deepusecode.character.dto.AuthResponse;
import org.example.deepusecode.character.dto.LoginRequest;
import org.example.deepusecode.character.dto.RegisterRequest;
import org.example.deepusecode.character.dto.UserVO;
import org.example.deepusecode.character.entity.User;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.security.LoginAttemptService;
import org.example.deepusecode.character.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    /** 用户名密码登录（带失败限流） */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest) {
        String attemptKey = request.getAccount() + "|" + clientIp(httpRequest);
        loginAttemptService.checkBlocked(attemptKey);

        User user;
        try {
            user = userService.login(request.getAccount(), request.getPassword());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                loginAttemptService.onFailure(attemptKey);
            }
            throw e;
        }

        loginAttemptService.onSuccess(attemptKey);
        String token = jwtUtil.generateToken(user.getId(), user.getAccount());
        return ApiResponse.success("登录成功", new AuthResponse(token, user.getId(), user.getAccount()));
    }

    /** 注册（当前版本不要求验证码） */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request.getAccount(), request.getPassword());
        String token = jwtUtil.generateToken(user.getId(), user.getAccount());
        return ApiResponse.success("注册成功", new AuthResponse(token, user.getId(), user.getAccount()));
    }

    /** 当前登录用户信息，前端用它校验 token 是否仍有效 */
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        User user = userService.findById(CurrentUser.id());
        return ApiResponse.success(new UserVO(user.getId(), user.getAccount(), user.getCreateTime()));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
