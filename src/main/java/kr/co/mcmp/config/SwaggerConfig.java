package kr.co.mcmp.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@EnableSwagger2
@OpenAPIDefinition(info = @Info(title = "M-CMP API", version = "v1"))
public class SwaggerConfig {

    public static final String AUTHORIZATION = "Authorization";

    @Bean
    public OpenAPI getApi() {
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(AUTHORIZATION);
        Components components = new Components()
                .addSecuritySchemes(AUTHORIZATION, new SecurityScheme()
                        .name(AUTHORIZATION)
                        .in(SecurityScheme.In.HEADER)
                        .type(SecurityScheme.Type.APIKEY));

        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
