package m.cmp.appManager._defaultconfig;

import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger Configuration 명세서 기본 정의
 */

@Configuration
public class SwaggerConfig {
        @Bean
        public OpenAPI openApiInfo(){
                return new OpenAPI().info(
                        new io.swagger.v3.oas.models.info.Info()
                                .title("mc-application-manager Api 명세서")
                                .description("mc-application-manager API 명세서")
                                .version("V1")
                );
        }
}
