package wiki.xmum.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录/注册成功返回：token + userInfo（与前端 net/index.js 约定一致）。
 */
@Data
@AllArgsConstructor
public class AuthVO {
    private String token;
    private UserInfoVO userInfo;
}
