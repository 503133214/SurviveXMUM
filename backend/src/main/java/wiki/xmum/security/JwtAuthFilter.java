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
import wiki.xmum.domain.po.User;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.util.JwtUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 从 Authorization: Bearer <token> 解析 JWT，构建 SecurityContext。
 * 登出后的 token 会进黑名单（Redis），此处校验。
 *
 * 解析成功后还会按 id 实时核对用户（主键查询，开销可忽略）：
 * - 已删除 / 已封禁 → 不注入认证，旧 token 立即失效（此前要等 token 自然过期，最长 7 天）；
 * - 角色取数据库实时值 → 提升/降级即刻生效，无需重新登录。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;
    private final UserMapper userMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, StringRedisTemplate redis, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.redis = redis;
        this.userMapper = userMapper;
    }

    /** 黑名单键用 SHA-256（此前用 32 位 hashCode，碰撞会误伤他人 token）。 */
    public static String blacklistKey(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return "jwt:blacklist:" + HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return "jwt:blacklist:" + Integer.toHexString(token.hashCode());
        }
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
                // 实时核对：删除/封禁即刻失效；角色/邮箱以数据库为准
                User user = userMapper.selectById(id);
                if (user == null
                        || (user.getDeleted() != null && user.getDeleted() == 1)
                        || "BANNED".equals(user.getStatus())) {
                    chain.doFilter(request, response);
                    return;
                }
                String role = user.getRole();
                AuthUser principal = new AuthUser(id, user.getEmail(), role);
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
