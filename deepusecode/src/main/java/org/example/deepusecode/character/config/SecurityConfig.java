package org.example.deepusecode.character.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.deepusecode.character.common.ApiResponse;
import org.example.deepusecode.character.common.ErrorCode;
import org.example.deepusecode.character.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 安全配置：JWT 无状态认证。
 * CORS 只在这里定义一份，不再和独立的 CorsFilter 重复。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:8082}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, HttpServletResponse.SC_UNAUTHORIZED,
                                        ErrorCode.UNAUTHORIZED, "未登录或登录已过期"))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, HttpServletResponse.SC_FORBIDDEN,
                                        ErrorCode.FORBIDDEN, "没有权限执行该操作"))
                )
                .authorizeHttpRequests(auth -> auth
                        /*
                         * SSE / 异步请求结束时容器会做一次 ASYNC 派发，重新走一遍过滤链。
                         * 此时 SecurityContext 已被清空，若不在这里放行，AuthorizationFilter
                         * 会抛 AccessDeniedException；而响应早已提交，Spring 无法再写错误页，
                         * 结果就是「事件都发出去了，但连接一直不关闭」。
                         */
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD,
                                DispatcherType.ERROR).permitAll()
                        // 认证入口：注册、登录、发验证码都放行
                        .requestMatchers(
                                "/user/login", "/user/login/**",
                                "/user/register", "/user/register/**",
                                "/user/code/**").permitAll()
                        // 广场浏览：游客可读
                        .requestMatchers(HttpMethod.GET, "/api/explore/**").permitAll()
                        // 健康检查给负载均衡 / 容器探针用
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // 错误页
                        .requestMatchers("/error", "/favicon.ico").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        /*
                         * 后端接口一律要求登录。
                         * 注意这里是显式列出 /api/**、/user/**、/actuator/**，
                         * 而不是用 anyRequest().authenticated()——因为单体部署后
                         * 同一个 jar 还要托管前端静态资源与 SPA 路由（/、/explore、/sets/3 ...），
                         * 那些请求必须放行，否则未登录用户打开首页会收到一坨 401 JSON。
                         * 真正的数据安全仍由这三条前缀保证，前端路由守卫只管跳转体验。
                         */
                        // 管理后台：必须是 ADMIN 权限（JWT 里的 role claim 决定 authority）
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**", "/user/**", "/actuator/**").authenticated()
                        // 其余：前端静态资源与 SPA 深链
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status,
                            ErrorCode code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
