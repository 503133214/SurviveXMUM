package wiki.xmum.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 存入 SecurityContext 的当前登录用户主体（principal）。
 */
@Data
@AllArgsConstructor
public class AuthUser {
    private Long id;
    private String email;
    private String role;   // USER / ADMIN
}
