package org.example.deepusecode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 排除 UserDetailsServiceAutoConfiguration：
 * 本项目用 JWT 无状态认证，不需要默认的内存用户，
 * 否则每次启动都会打印一段没用的 "Using generated security password"。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("org.example.deepusecode")
public class TestloginApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestloginApplication.class, args);
    }
}
