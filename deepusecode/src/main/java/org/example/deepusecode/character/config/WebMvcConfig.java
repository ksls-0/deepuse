package org.example.deepusecode.character.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Set;

/**
 * 单体部署：后端 jar 同时托管前端 dist（放在 classpath:/static/ 下）。
 *
 * <p>前端用的是 history 路由，刷新 /explore、/sets/3/fill 这类深链时
 * 服务器上并没有对应文件，必须回退到 index.html 交给前端路由处理。
 * 同时要保证 /api/**、/user/**、/actuator/** 这类后端路径不被吞掉——
 * 找不到就是真 404，而不是返回一个 HTML 页面。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 这些前缀属于后端，不做 SPA 回退 */
    private static final Set<String> RESERVED_PREFIXES = Set.of("api/", "user/", "actuator/", "error");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (isReserved(resourcePath)) {
                            return null; // 交给 Spring 走正常 404
                        }

                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }

                        // 没有对应文件且不像静态资源 => 当作前端路由，回退到 index.html
                        ClassPathResource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }

    private boolean isReserved(String resourcePath) {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        for (String prefix : RESERVED_PREFIXES) {
            if (path.equals(prefix.substring(0, prefix.length() - 1)) || path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
