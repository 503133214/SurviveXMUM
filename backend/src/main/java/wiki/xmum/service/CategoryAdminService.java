package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.dto.CategoryUpsertDTO;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.vo.CategoryAdminVO;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.util.TitleUtil;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分类管理（仅超管，控制器把关）。slug 已固化进 wiki_page.path，创建后不可修改；
 * 删除仅限空分类（含回收站页面也算占用，防止恢复后产生孤儿分类）。
 */
@Service
public class CategoryAdminService {

    private final WikiCategoryMapper categoryMapper;
    private final WikiPageMapper pageMapper;
    private final AuditService auditService;

    public CategoryAdminService(WikiCategoryMapper categoryMapper, WikiPageMapper pageMapper,
                                AuditService auditService) {
        this.categoryMapper = categoryMapper;
        this.pageMapper = pageMapper;
        this.auditService = auditService;
    }

    public List<CategoryAdminVO> list() {
        List<WikiCategory> cats = categoryMapper.selectList(null);
        cats.sort(Comparator
                .comparing((WikiCategory c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                .thenComparing(c -> c.getLabel() == null ? "" : c.getLabel()));
        // 各分类页面数（含回收站）：一次查全表 slug 分组
        Map<String, Long> countBySlug = pageMapper.selectList(Wrappers.<WikiPage>lambdaQuery()
                        .isNotNull(WikiPage::getCategorySlug)
                        .select(WikiPage::getCategorySlug))
                .stream().collect(Collectors.groupingBy(WikiPage::getCategorySlug, Collectors.counting()));
        return cats.stream()
                .map(c -> CategoryAdminVO.from(c, countBySlug.getOrDefault(c.getSlug(), 0L)))
                .toList();
    }

    public Long create(CategoryUpsertDTO dto) {
        String slug = TitleUtil.cleanTitle(dto.getSlug()); // slug 拼进路径，同标题字符规则
        if (slug.length() > 120) throw new BizException("分类标识过长（最多 120 字）");
        if (categoryMapper.selectCount(Wrappers.<WikiCategory>lambdaQuery()
                .eq(WikiCategory::getSlug, slug)) > 0) {
            throw new BizException("已存在同名分类：" + slug);
        }
        WikiCategory c = new WikiCategory();
        c.setSlug(slug);
        c.setLabel(blankOr(dto.getLabel(), slug));
        c.setIcon(blankOr(dto.getIcon(), "📁"));
        c.setDescription(trimToNull(dto.getDescription()));
        c.setSortOrder(dto.getSortOrder() == null ? 999 : dto.getSortOrder());
        categoryMapper.insert(c);
        auditService.log("CATEGORY_CREATE", "CATEGORY", c.getId(), "创建分类 " + slug);
        return c.getId();
    }

    public void update(Long id, CategoryUpsertDTO dto) {
        WikiCategory c = mustGet(id);
        // slug 不可改（会使既有页面路径断链）；仅展示属性可编辑
        if (dto.getLabel() != null && !dto.getLabel().isBlank()) c.setLabel(dto.getLabel().trim());
        if (dto.getIcon() != null) c.setIcon(blankOr(dto.getIcon(), "📁"));
        if (dto.getDescription() != null) c.setDescription(trimToNull(dto.getDescription()));
        if (dto.getSortOrder() != null) c.setSortOrder(dto.getSortOrder());
        categoryMapper.updateById(c);
        auditService.log("CATEGORY_UPDATE", "CATEGORY", c.getId(),
                "编辑分类 " + c.getSlug() + "（名称 " + c.getLabel() + "，排序 " + c.getSortOrder() + "）");
    }

    public void delete(Long id) {
        WikiCategory c = mustGet(id);
        long n = pageMapper.selectCount(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getCategorySlug, c.getSlug()));
        if (n > 0) throw new BizException("分类下还有 " + n + " 篇页面（含回收站），不能删除");
        categoryMapper.deleteById(id);
        auditService.log("CATEGORY_DELETE", "CATEGORY", id, "删除分类 " + c.getSlug());
    }

    private WikiCategory mustGet(Long id) {
        WikiCategory c = categoryMapper.selectById(id);
        if (c == null) throw new BizException(404, "分类不存在");
        return c;
    }

    private static String blankOr(String v, String dft) {
        return v == null || v.isBlank() ? dft : v.trim();
    }

    private static String trimToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
