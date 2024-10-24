package kr.co.mcmp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
// @EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurityConfigurer  {


	// @Override
    // public void configure(WebSecurity webSecurity) throws Exception {
	// 	webSecurity.ignoring().antMatchers("/resources/**", "/h2-console/**");
    // }

	// @Override
	// public void configure(HttpSecurity https) throws Exception {
	// 	https.authorizeRequests().antMatchers("/**").permitAll();
	// 	https.csrf().disable();
	// }	
	@Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                .frameOptions().disable()  // X-Frame-Options 비활성화
            )
            .cors().and()
            .csrf().disable()
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
