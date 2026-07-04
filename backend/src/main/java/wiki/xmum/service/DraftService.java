package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.DraftSaveDTO;
import wiki.xmum.domain.po.WikiDraft;
import wiki.xmum.domain.vo.DraftVO;
import wiki.xmum.mapper.WikiDraftMapper;
import wiki.xmum.util.JsonUtil;

import java.util.List;
import java.util.Map;

/**
 * 写文章草稿：半成品不做标题合法性校验（提交投稿时才校验），只做尺寸/数量上限。
 * 所有操作都以当前用户为边界（他人草稿一律 404）。
 */
@Service
public class DraftService {

    private static final int MAX_DRAFTS = 20;
    private static final int MAX_CONTENT = 300_000;

    private final WikiDraftMapper mapper;

    public DraftService(WikiDraftMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> save(DraftSaveDTO dto, Long userId) {
        validateSize(dto);
        String type = "UPDATE".equals(dto.getType()) ? "UPDATE" : "CREATE";
        String path = dto.getPath() == null || dto.getPath().isBlank() ? null : dto.getPath().trim();
        if (!"UPDATE".equals(type)) path = null; // 唯一键只用于编辑草稿

        WikiDraft target = null;
        if (dto.getId() != null) {
            target = owned(dto.getId(), userId); // 带 id 必须属于本人，否则 404
        }
        if (target == null && path != null) {
            target = byPath(userId, path);      // 编辑草稿按 (user, path) 收敛为一条
        }

        if (target == null) {
            if (countMine(userId) >= MAX_DRAFTS) {
                throw new BizException("草稿箱已满（最多 " + MAX_DRAFTS + " 份），请先删除旧草稿");
            }
            target = new WikiDraft();
            target.setUserId(userId);
        }
        apply(target, dto, type, path);
        try {
            if (target.getId() == null) mapper.insert(target);
            else mapper.updateById(target);
        } catch (DuplicateKeyException dup) {
            // 并发下同一页面的编辑草稿撞唯一键：改写到已存在的那条
            WikiDraft existing = byPath(userId, path);
            if (existing == null) throw dup;
            apply(existing, dto, type, path);
            mapper.updateById(existing);
            target = existing;
        }
        WikiDraft saved = mapper.selectById(target.getId());
        return Map.of("id", saved.getId(),
                "savedAt", DraftVO.from(saved).getUpdatedAt());
    }

    public List<DraftVO> listMine(Long userId) {
        return mapper.selectList(Wrappers.<WikiDraft>lambdaQuery()
                        .eq(WikiDraft::getUserId, userId)
                        .orderByDesc(WikiDraft::getUpdatedAt))
                .stream().map(DraftVO::from).toList();
    }

    public DraftVO get(Long id, Long userId) {
        WikiDraft d = owned(id, userId);
        if (d == null) throw new BizException(404, "草稿不存在");
        return DraftVO.detail(d);
    }

    /** 当前用户针对某个已有页面的编辑草稿；没有则返回 null（前端据此决定是否弹恢复提示）。 */
    public DraftVO getByPath(String path, Long userId) {
        if (path == null || path.isBlank()) return null;
        WikiDraft d = byPath(userId, path.trim());
        return d == null ? null : DraftVO.detail(d);
    }

    public void delete(Long id, Long userId) {
        mapper.delete(Wrappers.<WikiDraft>lambdaQuery()
                .eq(WikiDraft::getId, id)
                .eq(WikiDraft::getUserId, userId));
    }

    private WikiDraft owned(Long id, Long userId) {
        WikiDraft d = mapper.selectById(id);
        if (d == null) return null;
        if (!userId.equals(d.getUserId())) throw new BizException(404, "草稿不存在");
        return d;
    }

    private WikiDraft byPath(Long userId, String path) {
        if (path == null) return null;
        return mapper.selectOne(Wrappers.<WikiDraft>lambdaQuery()
                .eq(WikiDraft::getUserId, userId)
                .eq(WikiDraft::getTargetPath, path));
    }

    private long countMine(Long userId) {
        return mapper.selectCount(Wrappers.<WikiDraft>lambdaQuery().eq(WikiDraft::getUserId, userId));
    }

    private static void apply(WikiDraft d, DraftSaveDTO dto, String type, String path) {
        d.setType(type);
        d.setTargetPath(path);
        d.setCategorySlug(trimToNull(dto.getCategorySlug(), 120));
        d.setTitle(trimToNull(dto.getTitle(), 200));
        d.setIcon(trimToNull(dto.getIcon(), 40));
        d.setDescription(trimToNull(dto.getDescription(), 500));
        d.setTags(dto.getTags() == null ? "[]" : JsonUtil.toJson(dto.getTags()));
        d.setContent(dto.getContent());
        d.setBaseVersion(dto.getBaseVersion());
        d.setUpdatedAt(java.time.LocalDateTime.now()); // updateById 不会触发 DB 的 ON UPDATE 时也保底
    }

    private static void validateSize(DraftSaveDTO dto) {
        if (dto.getContent() != null && dto.getContent().length() > MAX_CONTENT) {
            throw new BizException("正文过长，无法保存草稿");
        }
        if (dto.getTags() != null && dto.getTags().size() > 10) {
            throw new BizException("标签最多 10 个");
        }
    }

    private static String trimToNull(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
