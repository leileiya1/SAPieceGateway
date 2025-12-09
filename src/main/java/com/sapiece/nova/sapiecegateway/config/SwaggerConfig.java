package com.sapiece.nova.sapiecegateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/Knife4j 配置类
 *
 * @author SAPiece
 * @since 2025-11-24
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SAPiece Gateway API")
                        .version("1.0.0")
                        .description("SAPiece Gateway 接口文档")
                        .contact(new Contact()
                                .name("SAPiece")
                                .url("https://github.com/sapiece")
                                .email("sapiece@example.com"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
