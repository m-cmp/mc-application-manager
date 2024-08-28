package kr.co.mcmp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/web/{spring:[a-zA-Z\\-]+}")
        .setViewName("forward:/index.html");
    registry.addViewController("/web/**/{spring:[a-zA-Z\\-]+}")
        .setViewName("forward:/index.html");
    registry.addViewController("/web/{spring:[a-zA-Z\\-]+}/**{spring:?!(\\.js|\\.css)$}")
        .setViewName("forward:/index.html");
    }
}
