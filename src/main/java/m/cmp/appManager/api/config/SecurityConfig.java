package m.cmp.appManager.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;

@EnableWebSecurity
public class SecurityConfig { 

	//인증없이 접근이 필요한 api를 여기에 추가하세요
	@Bean
	@Order(0)
	public SecurityFilterChain resources(HttpSecurity http) throws Exception {
	    return http.cors().configurationSource(corsConfigurationSource())
	    			.and()
	    			.csrf().disable()										// csrf 사용안함
	    			.requestMatchers(matchers -> matchers.antMatchers("/**"))
			        .requestCache(RequestCacheConfigurer::disable)
			        .securityContext(AbstractHttpConfigurer::disable)
			        .sessionManagement(AbstractHttpConfigurer::disable)
			        .build();
	}

	// CORS 허용 적용
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
		configuration.addAllowedHeader("*");
		configuration.addAllowedMethod("POST");
		configuration.addAllowedMethod("PUT");
		configuration.addAllowedMethod("DELETE");
		configuration.addAllowedMethod("GET");		
		
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
