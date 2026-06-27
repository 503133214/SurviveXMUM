package wiki.xmum.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import wiki.xmum.util.JwtUtil;

import java.io.IOException;
import java.util.List;

/**
 * 从 Authorization: Bearer <token> 解析 JWT，构建 SecurityContext。
 * 登出后的 token 会进黑名单（Redis），此处校验。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    public JwtAuthFilter(JwtUtil jwtUtil, StringRedisTemplate redis) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
    }

    public static String blacklistKey(String token) {
        return "jwt:blacklist:" + Integer.toHexString(token.hashCode());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (Boolean.TRUE.equals(redis.hasKey(blacklistKey(token)))) {
                    chain.doFilter(request, response);
                    return;
                }
                Claims claims = jwtUtil.parse(token);
                Long id = Long.valueOf(claims.getSubject());
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);
                AuthUser principal = new AuthUser(id, email, role);
                // 超管同时拥有 ROLE_SUPER_ADMIN 与 ROLE_ADMIN：既满足 super-only 规则，
                // 也满足所有现有 /admin/** 的 ADMIN 规则。
                List<SimpleGrantedAuthority> authorities =
                        "SUPER_ADMIN".equals(role)
                                ? List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"),
                                          new SimpleGrantedAuthority("ROLE_ADMIN"))
                                : List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // 无效 token → 视为未登录，受保护接口将返回 401
            }
        }
        chain.doFilter(request, response);
    }
}
