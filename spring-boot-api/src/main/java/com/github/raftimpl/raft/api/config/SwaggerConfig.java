package com.github.raftimpl.raft.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0配置类
 */
@Configuration
public class SwaggerConfig {

    @Value("${swagger.title:分布式存储系统API}")
    private String title;

    @Value("${swagger.description:基于Raft算法的分布式存储系统RESTful API}")
    private String description;

    @Value("${swagger.version:1.9.0}")
    private String version;

    @Value("${swagger.contact.name:raft-java}")
    private String contactName;

    @Value("${swagger.contact.email:1367016356@qq.com}")
    private String contactEmail;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name(contactName)
                                .email(contactEmail)))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
} 