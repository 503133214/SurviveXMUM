package wiki.xmum.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import wiki.xmum.domain.po.User;
import wiki.xmum.mapper.UserMapper;

/**
 * 启动时确保存在一个管理员账号（绕过校园邮箱限制）。
 * 通过环境变量配置：WIKI_ADMIN_EMAIL / WIKI_ADMIN_PASSWORD。
 * 若该邮箱已存在，则只确保其 role=ADMIN（不覆盖密码）。
 */
@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${wiki.admin.email:}")
    private String adminEmail;

    @Value("${wiki.admin.password:}")
    private String adminPassword;

    public AdminSeeder(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank()) {
            log.info("未配置 wiki.admin.email，跳过管理员初始化");
            return;
        }
        String email = adminEmail.toLowerCase();
        User existing = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
        if (existing != null) {
            if (!"ADMIN".equals(existing.getRole())) {
                existing.setRole("ADMIN");
                userMapper.updateById(existing);
                log.info("已将 {} 提升为管理员", email);
            }
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("管理员 {} 不存在但未配置 wiki.admin.password，无法创建", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setNickname("管理员");
        admin.setRole("ADMIN");
        admin.setStatus("ACTIVE");
        userMapper.insert(admin);
        log.info("已创建管理员账号 {}", email);
    }
}
