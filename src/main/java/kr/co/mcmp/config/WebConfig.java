package kr.co.mcmp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward all /web/** paths to index.html for SPA client-side routing.
        // Patterns with ** in the middle are not supported by Spring 6 PathPatternParser.
        registry.addViewController("/web/**")
                .setViewName("forward:/index.html");
    }
}
