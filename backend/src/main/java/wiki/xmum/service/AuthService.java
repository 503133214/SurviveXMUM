package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.LoginDTO;
import wiki.xmum.domain.dto.RegisterDTO;
import wiki.xmum.domain.dto.ResetPasswordDTO;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.vo.AuthVO;
import wiki.xmum.domain.vo.UserInfoVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.security.JwtAuthFilter;
import wiki.xmum.util.JwtUtil;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final VerificationCodeService codeService;
    private final StringRedisTemplate redis;

    /** 允许注册的校园邮箱域名（小写）。 */
    @Value("${wiki.allowed-email-domain:xmu.edu.my}")
    private String allowedDomain;

    @Value("${wiki.restrict-email-domain:true}")
    private boolean restrictDomain;

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       VerificationCodeService codeService, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.codeService = codeService;
        this.redis = redis;
    }

    private void validateEmailFormat(String email) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new BizException("邮箱格式不正确");
        }
    }

    private void validateSchoolEmail(String email) {
        validateEmailFormat(email);
        if (restrictDomain && !email.toLowerCase().endsWith("@" + allowedDomain)) {
            throw new BizException("仅允许使用校园邮箱（@" + allowedDomain + "）注册");
        }
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email.toLowerCase()));
    }

    /** 发送验证码：register 需校验校园邮箱且邮箱未注册；reset 需邮箱已注册。 */
    public void sendCode(String email, String type) {
        if ("register".equals(type)) {
            validateSchoolEmail(email);
            if (findByEmail(email) != null) {
                throw new BizException("该邮箱已注册，请直接登录");
            }
        } else if ("reset".equals(type)) {
            validateEmailFormat(email);
            if (findByEmail(email) == null) {
                throw new BizException("该邮箱尚未注册");
            }
        } else {
            throw new BizException("不支持的验证码类型");
        }
        codeService.send(email.toLowerCase(), type);
    }

    public AuthVO register(RegisterDTO dto) {
        String email = dto.getUserEmail().toLowerCase();
        validateSchoolEmail(email);
        if (findByEmail(email) != null) {
            throw new BizException("该邮箱已注册，请直接登录");
        }
        codeService.verify(email, "register", dto.getCode());

        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setNickname(email.substring(0, email.indexOf('@')));
        u.setRole("USER");
        u.setStatus("ACTIVE");
        userMapper.insert(u);

        return new AuthVO(jwtUtil.generate(u), UserInfoVO.from(u));
    }

    public AuthVO login(LoginDTO dto) {
        String email = dto.getUserEmail().toLowerCase();
        User u = findByEmail(email);
        if (u == null || !passwordEncoder.matches(dto.getPassword(), u.getPassword())) {
            throw new BizException("邮箱或密码错误");
        }
        if ("BANNED".equals(u.getStatus())) {
            throw new BizException("账号已被封禁，请联系管理员");
        }
        return new AuthVO(jwtUtil.generate(u), UserInfoVO.from(u));
    }

    public void resetPassword(ResetPasswordDTO dto) {
        String email = dto.getUserEmail().toLowerCase();
        User u = findByEmail(email);
        if (u == null) {
            throw new BizException("该邮箱尚未注册");
        }
        codeService.verify(email, "reset", dto.getCode());
        u.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(u);
    }

    /** 登出：把 token 放入黑名单，存活到其自然过期（这里简单存 7 天）。 */
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            redis.opsForValue().set(JwtAuthFilter.blacklistKey(token), "1", 7, TimeUnit.DAYS);
        }
    }

    public UserInfoVO userInfo(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(401, "用户不存在或登录已失效");
        }
        return UserInfoVO.from(u);
    }
}
