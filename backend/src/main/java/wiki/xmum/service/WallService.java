package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.WallEntryDTO;
import wiki.xmum.domain.po.WallEntry;
import wiki.xmum.mapper.WallEntryMapper;

import java.util.List;

@Service
public class WallService {

    private final WallEntryMapper mapper;
    private final AuditService auditService;

    public WallService(WallEntryMapper mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
    }

    /** 按 sort_order 升序、创建时间倒序返回全部条目（公开展示与后台通用）。 */
    public List<WallEntry> list() {
        return mapper.selectList(Wrappers.<WallEntry>lambdaQuery()
                .orderByAsc(WallEntry::getSortOrder)
                .orderByDesc(WallEntry::getCreatedAt));
    }

    public Long create(WallEntryDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) throw new BizException("名称不能为空");
        WallEntry e = new WallEntry();
        apply(e, dto, name);
        mapper.insert(e);
        auditService.log("WALL_CREATE", "WALL", e.getId(), "新增致谢墙条目「" + name + "」");
        return e.getId();
    }

    public void update(Long id, WallEntryDTO dto) {
        WallEntry e = mapper.selectById(id);
        if (e == null) throw new BizException(404, "条目不存在");
        String name = dto.getName() == null ? e.getName() : dto.getName().trim();
        if (name == null || name.isEmpty()) throw new BizException("名称不能为空");
        apply(e, dto, name);
        mapper.updateById(e);
        auditService.log("WALL_UPDATE", "WALL", e.getId(), "编辑致谢墙条目「" + name + "」");
    }

    public void delete(Long id) {
        WallEntry e = mapper.selectById(id);
        mapper.deleteById(id);
        auditService.log("WALL_DELETE", "WALL", id,
                "删除致谢墙条目「" + (e == null ? id : e.getName()) + "」");
    }

    private static void apply(WallEntry e, WallEntryDTO dto, String name) {
        e.setName(name);
        e.setAvatar(blankToNull(dto.getAvatar()));
        e.setDescription(blankToNull(dto.getDescription()));
        e.setLink(blankToNull(dto.getLink()));
        e.setCategory(blankToNull(dto.getCategory()));
        e.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
