package org.example.deepusecode.character.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.BusinessException;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.config.JwtUtil;
import org.example.deepusecode.character.dto.AccountPatterns;
import org.example.deepusecode.character.dto.AuthResponse;
import org.example.deepusecode.character.dto.EmailLoginRequest;
import org.example.deepusecode.character.dto.EmailRegisterRequest;
import org.example.deepusecode.character.dto.PhoneLoginRequest;
import org.example.deepusecode.character.dto.PhoneRegisterRequest;
import org.example.deepusecode.character.dto.SendCodeRequest;
import org.example.deepusecode.character.dto.UserVO;
import org.example.deepusecode.character.entity.User;
import org.example.deepusecode.character.security.CurrentUser;
import org.example.deepusecode.character.security.LoginAttemptService;
import org.example.deepusecode.character.service.UserService;
import org.example.deepusecode.character.verifycode.CodeChannel;
import org.example.deepusecode.character.verifycode.VerifyCodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

/**
 * 认证入口。邮箱与手机号是两条独立通道：
 * <pre>
 * POST /user/code/email        发邮箱验证码
 * POST /user/code/phone        发手机验证码
 * POST /user/register/email    邮箱注册（需验证码）
 * POST /user/register/phone    手机号注册（需验证码）
 * POST /user/login/email       邮箱登录
 * POST /user/login/phone       手机号登录
 * GET  /user/me                当前用户
 * </pre>
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final VerifyCodeService verifyCodeService;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;

    // ===================== 发送验证码 =====================

    @PostMapping("/code/email")
    public ApiResponse<Void> sendEmailCode(@Valid @RequestBody SendCodeRequest request) {
        String email = normalize(request.getTarget());
        if (!email.matches(AccountPatterns.EMAIL)) {
            throw BusinessException.badRequest("邮箱格式不正确");
        }
        verifyCodeService.sendCode(email, CodeChannel.EMAIL);
        return ApiResponse.success("验证码已发送", null);
    }

    @PostMapping("/code/phone")
    public ApiResponse<Void> sendPhoneCode(@Valid @RequestBody SendCodeRequest request) {
        String phone = normalize(request.getTarget());
        if (!phone.matches(AccountPatterns.PHONE)) {
            throw BusinessException.badRequest("手机号格式不正确");
        }
        verifyCodeService.sendCode(phone, CodeChannel.PHONE);
        return ApiResponse.success("验证码已发送", null);
    }

    // ===================== 注册 =====================

    @PostMapping("/register/email")
    public ApiResponse<AuthResponse> registerByEmail(@Valid @RequestBody EmailRegisterRequest request) {
        String email = normalize(request.getEmail());
        // 先校验验证码：校验通过后验证码立即作废
        verifyCodeService.verifyCode(email, CodeChannel.EMAIL, request.getCode());
        User user = userService.registerByEmail(email, request.getPassword());
        return ApiResponse.success("注册成功", toAuth(user));
    }

    @PostMapping("/register/phone")
    public ApiResponse<AuthResponse> registerByPhone(@Valid @RequestBody PhoneRegisterRequest request) {
        String phone = normalize(request.getPhone());
        verifyCodeService.verifyCode(phone, CodeChannel.PHONE, request.getCode());
        User user = userService.registerByPhone(phone, request.getPassword());
        return ApiResponse.success("注册成功", toAuth(user));
    }

    // ===================== 登录 =====================

    @PostMapping("/login/email")
    public ApiResponse<AuthResponse> loginByEmail(@Valid @RequestBody EmailLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        String email = normalize(request.getEmail());
        return login("email", email,
                () -> userService.loginByEmail(email, request.getPassword()), httpRequest);
    }

    @PostMapping("/login/phone")
    public ApiResponse<AuthResponse> loginByPhone(@Valid @RequestBody PhoneLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        String phone = normalize(request.getPhone());
        return login("phone", phone,
                () -> userService.loginByPhone(phone, request.getPassword()), httpRequest);
    }

    // ===================== 当前用户 =====================

    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        User user = userService.findById(CurrentUser.id());
        return ApiResponse.success(new UserVO(user.getId(), user.getAccount(), user.getCreateTime()));
    }

    // ===================== 内部 =====================

    /**
     * 邮箱 / 手机号两条登录通道共用：限流、失败计数、签发 token。
     */
    private ApiResponse<AuthResponse> login(String channel, String target,
                                            Supplier<User> action,
                                            HttpServletRequest httpRequest) {
        String attemptKey = channel + ":" + target + "|" + clientIp(httpRequest);
        loginAttemptService.checkBlocked(attemptKey);

        User user;
        try {
            user = action.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                loginAttemptService.onFailure(attemptKey);
            }
            throw e;
        }

        loginAttemptService.onSuccess(attemptKey);
        return ApiResponse.success("登录成功", toAuth(user));
    }

    private AuthResponse toAuth(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getAccount());
        return new AuthResponse(token, user.getId(), user.getAccount());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
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
