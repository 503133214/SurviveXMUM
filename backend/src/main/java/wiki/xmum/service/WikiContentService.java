package wiki.xmum.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import wiki.xmum.common.BizException;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.domain.vo.*;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.util.JsonUtil;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 内容只读：构建 manifest（对齐前端 wiki.data.js 形状）与单页详情。
 */
@Service
public class WikiContentService {

    private static final String HOME_PATH = "README";

    private final WikiCategoryMapper categoryMapper;
    private final WikiPageMapper pageMapper;

    public WikiContentService(WikiCategoryMapper categoryMapper, WikiPageMapper pageMapper) {
        this.categoryMapper = categoryMapper;
        this.pageMapper = pageMapper;
    }

    private static String iso(java.time.LocalDateTime dt) {
        if (dt == null) return null;
        return dt.atOffset(ZoneOffset.ofHours(8)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private PageNodeVO toNode(WikiPage p) {
        PageNodeVO n = new PageNodeVO();
        n.setTitle(p.getTitle());
        n.setPath(p.getPath());
        n.setCategory(p.getCategorySlug() == null ? "" : p.getCategorySlug());
        n.setIcon(p.getIcon() == null ? "" : p.getIcon());
        n.setOrder(p.getSortOrder());
        n.setDescription(p.getDescription() == null ? "" : p.getDescription());
        n.setTags(JsonUtil.toStringList(p.getTags()));
        n.setHeadings(JsonUtil.toStringList(p.getHeadings()));
        n.setDraft(false);
        n.setLastUpdated(iso(p.getUpdatedAt()));
        n.setViewCount(p.getViewCount() == null ? 0 : p.getViewCount());
        return n;
    }

    private static final Comparator<WikiPage> PAGE_ORDER = Comparator
            .comparing((WikiPage p) -> p.getSortOrder() == null ? Integer.MAX_VALUE : p.getSortOrder())
            .thenComparing(p -> p.getTitle() == null ? "" : p.getTitle());

    public ManifestVO getManifest() {
        List<WikiCategory> categories = categoryMapper.selectList(null);
        categories.sort(Comparator
                .comparing((WikiCategory c) -> c.getSortOrder() == null ? Integer.MAX_VALUE : c.getSortOrder())
                .thenComparing(c -> c.getLabel() == null ? "" : c.getLabel()));

        List<WikiPage> allPages = pageMapper.selectList(
                Wrappers.<WikiPage>lambdaQuery()
                        .eq(WikiPage::getStatus, "PUBLISHED")
                        .eq(WikiPage::getDeleted, 0));

        // home: 根 README
        WikiPage homePage = allPages.stream().filter(p -> HOME_PATH.equals(p.getPath())).findFirst().orElse(null);

        // 顶层页面（无分类，排除 README）
        List<WikiPage> topPages = allPages.stream()
                .filter(p -> (p.getCategorySlug() == null || p.getCategorySlug().isEmpty()) && !HOME_PATH.equals(p.getPath()))
                .sorted(PAGE_ORDER)
                .toList();

        List<Object> tree = new ArrayList<>();
        for (WikiPage p : topPages) tree.add(toNode(p));

        List<PageNodeVO> flat = new ArrayList<>();
        // 树的扁平阅读顺序：先顶层页，再各分类下的页
        for (WikiPage p : topPages) flat.add(toNode(p));

        for (WikiCategory c : categories) {
            List<WikiPage> children = allPages.stream()
                    .filter(p -> c.getSlug().equals(p.getCategorySlug()))
                    .sorted(PAGE_ORDER)
                    .toList();
            if (children.isEmpty()) continue;
            CategoryNodeVO node = new CategoryNodeVO();
            node.setSlug(c.getSlug());
            node.setLabel(c.getLabel());
            node.setIcon(c.getIcon() == null ? "📁" : c.getIcon());
            node.setOrder(c.getSortOrder());
            node.setDescription(c.getDescription() == null ? "" : c.getDescription());
            List<Object> kids = new ArrayList<>();
            for (WikiPage p : children) {
                PageNodeVO kn = toNode(p);
                kids.add(kn);
                flat.add(kn);
            }
            node.setChildren(kids);
            tree.add(node);
        }

        PageNodeVO homeNode = homePage == null ? null : toNode(homePage);
        if (homeNode != null) {
            if (homeNode.getTitle() == null || homeNode.getTitle().isEmpty()) homeNode.setTitle("首页");
            flat.add(0, homeNode);
        }

        ManifestVO vo = new ManifestVO();
        vo.setTree(tree);
        vo.setPages(flat);
        vo.setHome(homeNode);
        vo.setGeneratedAt(java.time.Instant.now().toString());
        return vo;
    }

    public PageDetailVO getPage(String path, boolean track) {
        if (path == null || path.isBlank()) path = HOME_PATH;
        WikiPage p = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, path)
                .eq(WikiPage::getDeleted, 0));
        if (p == null) {
            throw new BizException(404, "页面不存在：" + path);
        }
        // 仅在真正“阅读”文档时计数（track=true，由 DocPage 传入；编辑页加载正文不计）。
        // 原子自增（setSql 避免读改写竞态），匿名 + 登录都计一次。
        int current = p.getViewCount() == null ? 0 : p.getViewCount();
        if (track) {
            try {
                pageMapper.update(null, Wrappers.<WikiPage>lambdaUpdate()
                        .setSql("view_count = view_count + 1")
                        .eq(WikiPage::getId, p.getId()));
            } catch (Exception ignore) {
                // 计数非关键，失败不影响正文返回
            }
        }
        PageDetailVO vo = new PageDetailVO();
        vo.setId(p.getId());
        vo.setPath(p.getPath());
        vo.setTitle(p.getTitle());
        vo.setCategory(p.getCategorySlug() == null ? "" : p.getCategorySlug());
        vo.setCategorySlug(p.getCategorySlug());
        vo.setIcon(p.getIcon());
        vo.setDescription(p.getDescription());
        vo.setTags(JsonUtil.toStringList(p.getTags()));
        vo.setHeadings(JsonUtil.toStringList(p.getHeadings()));
        vo.setContent(p.getContent());
        vo.setVersion(p.getVersion() == null ? 0 : p.getVersion());
        vo.setLastUpdated(iso(p.getUpdatedAt()));
        vo.setViewCount(track ? current + 1 : current);
        return vo;
    }
}
