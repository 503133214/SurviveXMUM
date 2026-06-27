package wiki.xmum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.UserUpsertDTO;
import wiki.xmum.domain.po.User;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.domain.vo.UserAdminVO;
import wiki.xmum.mapper.UserMapper;
import wiki.xmum.security.AuthUser;

import java.util.regex.Pattern;

@Service
public class UserAdminService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${wiki.admin.email:}")
    private String seedAdminEmail;

    public UserAdminService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserAdminVO> list(String keyword, String role, String status,
                                        boolean includeDeleted, long page, long size) {
        LambdaQueryWrapper<User> q = Wrappers.<User>lambdaQuery();
        if (!includeDeleted) q.eq(User::getDeleted, 0);
        if (role != null && !role.isBlank()) q.eq(User::getRole, role);
        if (status != null && !status.isBlank()) q.eq(User::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            q.and(w -> w.like(User::getEmail, keyword).or().like(User::getNickname, keyword));
        }
        q.orderByDesc(User::getCreatedAt);
        Page<User> p = userMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)), q);
        return new PageResult<>(p.getRecords().stream().map(UserAdminVO::from).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }

    public Long create(UserUpsertDTO dto) {
        String email = require(dto.getEmail(), "邮箱不能为空").toLowerCase();
        if (!EMAIL.matcher(email).matches()) throw new BizException("邮箱格式不正确");
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            throw new BizException("密码至少 6 位");
        }
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) > 0) {
            throw new BizException("该邮箱已存在");
        }
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(dto.getPassword()));
        u.setNickname(blankToNull(dto.getNickname()) != null
                ? dto.getNickname() : email.substring(0, email.indexOf('@')));
        u.setRole(normalizeRole(dto.getRole()));
        u.setStatus(normalizeStatus(dto.getStatus()));
        u.setDeleted(0);
        userMapper.insert(u);
        return u.getId();
    }

    public void update(Long id, UserUpsertDTO dto, AuthUser actor) {
        User u = mustGet(id);
        boolean isSelf = u.getId().equals(actor.getId());
        boolean isSeed = seedAdminEmail != null && seedAdminEmail.equalsIgnoreCase(u.getEmail());
        String newRole = normalizeRole(dto.getRole() == null ? u.getRole() : dto.getRole());
        String newStatus = normalizeStatus(dto.getStatus() == null ? u.getStatus() : dto.getStatus());

        if (isSelf && !"ADMIN".equals(newRole)) throw new BizException("不能降级自己的管理员权限");
        if (isSelf && "BANNED".equals(newStatus)) throw new BizException("不能封禁自己");
        if (isSeed && (!"ADMIN".equals(newRole) || "BANNED".equals(newStatus))) {
            throw new BizException("初始管理员账号不可降级或封禁");
        }
        boolean wasActiveAdmin = "ADMIN".equals(u.getRole()) && "ACTIVE".equals(u.getStatus());
        boolean willBeActiveAdmin = "ADMIN".equals(newRole) && "ACTIVE".equals(newStatus);
        if (wasActiveAdmin && !willBeActiveAdmin && countActiveAdminsExcept(u.getId()) == 0) {
            throw new BizException("至少需保留一名在岗管理员");
        }

        if (dto.getNickname() != null) u.setNickname(blankToNull(dto.getNickname()));
        u.setRole(newRole);
        u.setStatus(newStatus);
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 6) throw new BizException("密码至少 6 位");
            u.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(u);
    }

    public void softDelete(Long id, AuthUser actor) {
        User u = mustGet(id);
        if (u.getId().equals(actor.getId())) throw new BizException("不能删除自己");
        if (seedAdminEmail != null && seedAdminEmail.equalsIgnoreCase(u.getEmail())) {
            throw new BizException("初始管理员账号不可删除");
        }
        if ("ADMIN".equals(u.getRole()) && "ACTIVE".equals(u.getStatus())
                && countActiveAdminsExcept(u.getId()) == 0) {
            throw new BizException("至少需保留一名在岗管理员");
        }
        u.setDeleted(1);
        userMapper.updateById(u);
    }

    public void restore(Long id) {
        User u = mustGet(id);
        u.setDeleted(0);
        userMapper.updateById(u);
    }

    private long countActiveAdminsExcept(Long excludeId) {
        return userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getRole, "ADMIN")
                .eq(User::getStatus, "ACTIVE")
                .eq(User::getDeleted, 0)
                .ne(User::getId, excludeId));
    }

    private User mustGet(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new BizException(404, "用户不存在");
        return u;
    }

    private static int clampSize(long size) {
        if (size < 1) return 20;
        return (int) Math.min(size, 100);
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new BizException(message);
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) ? "ADMIN" : "USER";
    }

    private static String normalizeStatus(String status) {
        return "BANNED".equalsIgnoreCase(status) ? "BANNED" : "ACTIVE";
    }
}
