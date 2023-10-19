package m.cmp.appManager._defaultconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;



/**
 * Spring Security 설정
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SecurityFilterChain 설정 Bean
     * @param httpSecurity
     * @return
     * @throws Exception


     [SAMPLE]
     @Bean
     public SecurityFilterChain mmpStartKitWebSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                 .authorizeRequests()
                 .antMatchers("/api/test1", "/api/test2").permitAll()  // 허용되는 uri 패턴
                 .anyRequest().authenticated()    // 허용되는 uri를 제외한 나머지 요청
                 .and()
                 .csrf().disable() // site 간 요청 위조 기본 사용 안함. ( 보안 처리상 사용 해야함. )
                 .formLogin().disable() // loginform 사용 여부 ( 사용 안함. )
                 .logout().disable() // logout 사용 안함.
                 .cors().disable() // cross origin resource sharing 사용 안함. ( 보안 처리상 사용 해야함. )
                 .cors().configurationSource(configurationSource()) // cors 설정 적용시 사용.
                 .build();
     }
     */
    @Bean
    public SecurityFilterChain mmpStartKitWebSecurityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                .authorizeRequests()
                .antMatchers("/api/sample").permitAll()
                .anyRequest().authenticated()
                .and()
                .build();
    }

    /**
     * Custom WebSecurity 설정 Bean ( 필요시 Uri Path 추가 후 사용 : 기본 actuator, swagger 처리)
     * @return
     */
    @Bean
    public WebSecurityCustomizer mmpStartKitWebSecurityCustomizer(){
        return (httpUri) -> httpUri.ignoring().antMatchers("/mmpact/**","/swagger-ui/**","/V1/api-docs/**");
    }

    /**
     * CORS 상세 설정 Bean

    [SAMPLE]

    @Bean
    public CorsConfigurationSource configurationSource(){
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOriginPatterns(Arrays.asList("*"));
        corsConfiguration.setAllowedMethods(Arrays.asList("POST","GET","DELETE","PUT"));
        corsConfiguration.setAllowedHeaders(Arrays.asList("*"));
        corsConfiguration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);
        return urlBasedCorsConfigurationSource;
    }
    */



}
