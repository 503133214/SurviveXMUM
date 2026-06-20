package wiki.xmum.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import wiki.xmum.common.ApiResponse;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开：认证相关
                .requestMatchers("/login", "/register", "/send/code", "/password/reset").permitAll()
                // 公开：wiki 内容只读
                .requestMatchers(HttpMethod.GET, "/wiki/manifest", "/wiki/page").permitAll()
                // 健康检查
                .requestMatchers("/health", "/actuator/health").permitAll()
                // 管理端
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 其余需登录
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeJson(res, 401, "请先登录"))
                .accessDeniedHandler((req, res, e) -> writeJson(res, 403, "没有权限执行该操作")))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 以 HTTP 200 + 统一封装返回，前端按 data.code 判断（401/403）。 */
    private void writeJson(HttpServletResponse res, int code, String msg) throws java.io.IOException {
        res.setStatus(HttpServletResponse.SC_OK);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, msg)));
    }
}
