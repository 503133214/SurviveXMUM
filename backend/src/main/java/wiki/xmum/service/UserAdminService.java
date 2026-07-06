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
    private final AuditService auditService;
    private final wiki.xmum.mapper.UserFavoriteMapper favoriteMapper;
    private final wiki.xmum.mapper.UserViewHistoryMapper historyMapper;
    private final wiki.xmum.mapper.NotificationMapper notificationMapper;
    private final wiki.xmum.mapper.WikiDraftMapper draftMapper;

    @Value("${wiki.admin.email:}")
    private String seedAdminEmail;

    public UserAdminService(UserMapper userMapper, PasswordEncoder passwordEncoder, AuditService auditService,
                            wiki.xmum.mapper.UserFavoriteMapper favoriteMapper,
                            wiki.xmum.mapper.UserViewHistoryMapper historyMapper,
                            wiki.xmum.mapper.NotificationMapper notificationMapper,
                            wiki.xmum.mapper.WikiDraftMapper draftMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.favoriteMapper = favoriteMapper;
        this.historyMapper = historyMapper;
        this.notificationMapper = notificationMapper;
        this.draftMapper = draftMapper;
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
        auditService.log("USER_CREATE", "USER", u.getId(),
                "创建用户 " + email + "（角色 " + u.getRole() + "）");
        return u.getId();
    }

    public void update(Long id, UserUpsertDTO dto, AuthUser actor) {
        User u = mustGet(id);
        boolean isSelf = u.getId().equals(actor.getId());
        boolean isSeed = seedAdminEmail != null && seedAdminEmail.equalsIgnoreCase(u.getEmail());
        String newRole = normalizeRole(dto.getRole() == null ? u.getRole() : dto.getRole());
        String newStatus = normalizeStatus(dto.getStatus() == null ? u.getStatus() : dto.getStatus());

        if (isSelf && !newRole.equals(u.getRole())) throw new BizException("不能修改自己的角色");
        if (isSelf && "BANNED".equals(newStatus)) throw new BizException("不能封禁自己");
        if (isSeed && (!"SUPER_ADMIN".equals(newRole) || "BANNED".equals(newStatus))) {
            throw new BizException("初始超级管理员不可降级或封禁");
        }
        boolean wasActiveAdmin = isAdminRole(u.getRole()) && "ACTIVE".equals(u.getStatus());
        boolean willBeActiveAdmin = isAdminRole(newRole) && "ACTIVE".equals(newStatus);
        if (wasActiveAdmin && !willBeActiveAdmin && countActiveAdminsExcept(u.getId()) == 0) {
            throw new BizException("至少需保留一名在岗管理员");
        }

        // 变更明细（角色/状态/改密）先于赋值收集，供审计
        StringBuilder changes = new StringBuilder();
        if (!newRole.equals(u.getRole())) changes.append("角色 ").append(u.getRole()).append("→").append(newRole).append("；");
        if (!newStatus.equals(u.getStatus())) changes.append("状态 ").append(u.getStatus()).append("→").append(newStatus).append("；");
        boolean pwChanged = dto.getPassword() != null && !dto.getPassword().isBlank();
        if (pwChanged) changes.append("重置密码；");

        if (dto.getNickname() != null) u.setNickname(blankToNull(dto.getNickname()));
        u.setRole(newRole);
        u.setStatus(newStatus);
        if (pwChanged) {
            if (dto.getPassword().length() < 6) throw new BizException("密码至少 6 位");
            u.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userMapper.updateById(u);
        auditService.log("USER_UPDATE", "USER", u.getId(),
                "更新用户 " + u.getEmail() + (changes.length() > 0 ? "：" + changes : ""));
    }

    public void softDelete(Long id, AuthUser actor) {
        User u = mustGet(id);
        if (u.getId().equals(actor.getId())) throw new BizException("不能删除自己");
        if (seedAdminEmail != null && seedAdminEmail.equalsIgnoreCase(u.getEmail())) {
            throw new BizException("初始超级管理员不可删除");
        }
        if (isAdminRole(u.getRole()) && "ACTIVE".equals(u.getStatus())
                && countActiveAdminsExcept(u.getId()) == 0) {
            throw new BizException("至少需保留一名在岗管理员");
        }
        u.setDeleted(1);
        userMapper.updateById(u);
        auditService.log("USER_DELETE", "USER", u.getId(), "删除用户 " + u.getEmail());
    }

    public void restore(Long id) {
        User u = mustGet(id);
        u.setDeleted(0);
        userMapper.updateById(u);
        auditService.log("USER_RESTORE", "USER", u.getId(), "恢复用户 " + u.getEmail());
    }

    /**
     * 彻底删除（不可恢复）：仅允许对已软删用户执行；级联清其个人数据
     * （收藏/浏览历史/通知/草稿），保留其投稿(wiki_revision 有 author_email 快照)
     * 与审计记录（actor_email 快照）作历史追溯。
     */
    @org.springframework.transaction.annotation.Transactional
    public void purge(Long id) {
        User u = mustGet(id);
        if (u.getDeleted() == null || u.getDeleted() != 1) {
            throw new BizException("请先删除用户（软删除）后再彻底删除");
        }
        if (seedAdminEmail != null && seedAdminEmail.equalsIgnoreCase(u.getEmail())) {
            throw new BizException("初始超级管理员不可彻底删除");
        }
        int favs = favoriteMapper.delete(Wrappers.<wiki.xmum.domain.po.UserFavorite>lambdaQuery()
                .eq(wiki.xmum.domain.po.UserFavorite::getUserId, id));
        int hists = historyMapper.delete(Wrappers.<wiki.xmum.domain.po.UserViewHistory>lambdaQuery()
                .eq(wiki.xmum.domain.po.UserViewHistory::getUserId, id));
        int notifs = notificationMapper.delete(Wrappers.<wiki.xmum.domain.po.Notification>lambdaQuery()
                .eq(wiki.xmum.domain.po.Notification::getUserId, id));
        int drafts = draftMapper.delete(Wrappers.<wiki.xmum.domain.po.WikiDraft>lambdaQuery()
                .eq(wiki.xmum.domain.po.WikiDraft::getUserId, id));
        userMapper.deleteById(id);
        auditService.log("USER_PURGE", "USER", id,
                "彻底删除用户 " + u.getEmail() + "（清理收藏 " + favs + "、历史 " + hists
                        + "、通知 " + notifs + "、草稿 " + drafts + "；投稿记录保留）");
    }

    private long countActiveAdminsExcept(Long excludeId) {
        return userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .in(User::getRole, java.util.List.of("ADMIN", "SUPER_ADMIN"))
                .eq(User::getStatus, "ACTIVE")
                .eq(User::getDeleted, 0)
                .ne(User::getId, excludeId));
    }

    private static boolean isAdminRole(String role) {
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
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
        if ("SUPER_ADMIN".equalsIgnoreCase(role)) return "SUPER_ADMIN";
        if ("ADMIN".equalsIgnoreCase(role)) return "ADMIN";
        return "USER";
    }

    private static String normalizeStatus(String status) {
        return "BANNED".equalsIgnoreCase(status) ? "BANNED" : "ACTIVE";
    }
}
