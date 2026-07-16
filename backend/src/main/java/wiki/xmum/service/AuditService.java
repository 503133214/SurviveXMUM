package wiki.xmum.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import wiki.xmum.common.DateRangeFilter;
import wiki.xmum.domain.po.AuditLog;
import wiki.xmum.domain.vo.AuditVO;
import wiki.xmum.domain.vo.PageResult;
import wiki.xmum.mapper.AuditLogMapper;
import wiki.xmum.security.AuthUser;
import wiki.xmum.security.CurrentUser;

/**
 * 管理操作审计。写入尽力而为（吞异常，绝不阻断业务）；查询仅超管（控制器把关）。
 */
@Service
public class AuditService {

    private final AuditLogMapper mapper;

    public AuditService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 记录一条管理操作；操作者取当前请求用户（无则记 null，如系统任务）。 */
    public void log(String action, String targetType, Long targetId, String detail) {
        try {
            mapper.insert(entry(action, targetType, targetId, detail));
        } catch (Exception ignore) {
            // 审计失败不影响主流程
        }
    }

    /**
     * 必须成功的审计写入。仅用于不可逆敏感操作，由调用方事务保证审计失败时业务操作一起回滚。
     */
    public void logStrict(String action, String targetType, Long targetId, String detail) {
        mapper.insert(entry(action, targetType, targetId, detail));
    }

    private static AuditLog entry(String action, String targetType, Long targetId, String detail) {
        AuthUser actor = CurrentUser.getOrNull();
        AuditLog a = new AuditLog();
        a.setActorId(actor == null ? null : actor.getId());
        a.setActorEmail(actor == null ? null : actor.getEmail());
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setDetail(detail == null ? null
                : (detail.length() > 500 ? detail.substring(0, 500) : detail));
        return a;
    }

    /** 分页查询：日期闭区间含当天；keyword 命中操作者邮箱/详情；action 精确匹配。 */
    public PageResult<AuditVO> query(String from, String to, String keyword, String action,
                                     long page, long size) {
        LambdaQueryWrapper<AuditLog> q = Wrappers.lambdaQuery();
        DateRangeFilter.Range dates = DateRangeFilter.parse(from, to);
        if (dates.from() != null) q.ge(AuditLog::getCreatedAt, dates.from().atStartOfDay());
        if (dates.to() != null) q.lt(AuditLog::getCreatedAt, dates.to().plusDays(1).atStartOfDay());
        if (action != null && !action.isBlank()) q.eq(AuditLog::getAction, action.trim());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            q.and(w -> w.like(AuditLog::getActorEmail, kw).or().like(AuditLog::getDetail, kw));
        }
        q.orderByDesc(AuditLog::getCreatedAt);
        Page<AuditLog> p = mapper.selectPage(new Page<>(Math.max(1, page), Math.min(Math.max(1, size), 100)), q);
        return new PageResult<>(p.getRecords().stream().map(AuditVO::from).toList(),
                p.getTotal(), p.getCurrent(), p.getSize());
    }
}
