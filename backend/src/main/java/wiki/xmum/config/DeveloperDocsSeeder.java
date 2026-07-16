package wiki.xmum.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.CommandLineRunner;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.service.PageVersionService;
import wiki.xmum.util.JsonUtil;
import wiki.xmum.util.MarkdownUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Safely fills the built-in developer documentation on existing installations.
 *
 * The Wiki is database-driven, so classpath Markdown cannot be served directly.
 * This seeder only updates the two known empty v0 placeholders, creates missing
 * built-in pages, and never overwrites later administrator edits.
 */
@Component
@Order(20)
public class DeveloperDocsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DeveloperDocsSeeder.class);
    private static final String CATEGORY_SLUG = "api";
    private static final String ORIGINAL_TEST_PLACEHOLDER = """
            # test3
            这是一篇test3文章
            ![_](https://surivivexmum.wiki/wiki/images/2026/06/61c949cf-c5db-49ad-9680-a84ae7ba1538.jpg =267x)

            hello
            ![Messi in 2010_](https://surivivexmum.wiki/wiki/images/2026/06/03d35a0e-d410-462e-9e0e-fe6e5b22727f.jpg =276x)

            你好
            """;

    private final WikiCategoryMapper categoryMapper;
    private final WikiPageMapper pageMapper;
    private final PageVersionService pageVersionService;

    public DeveloperDocsSeeder(WikiCategoryMapper categoryMapper, WikiPageMapper pageMapper,
                               PageVersionService pageVersionService) {
        this.categoryMapper = categoryMapper;
        this.pageMapper = pageMapper;
        this.pageVersionService = pageVersionService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Long categoryId = ensureCategory();
        for (DocSeed seed : seeds()) seedPage(seed, categoryId);
        retireTestPlaceholder();
    }

    private List<DocSeed> seeds() {
        return List.of(
                new DocSeed("api/api-overview", "API 概览", "🧭",
                        "站点架构、鉴权、数据约定与最小调用示例。",
                        List.of("API", "开发", "架构"), 1,
                        read("wiki-content/api/api-overview.md")),
                new DocSeed("api/endpoints", "接口参考", "📚",
                        "按权限分组的完整后端接口、请求字段与错误码参考。",
                        List.of("API", "接口", "开发"), 2,
                        read("wiki-content/api/endpoints.md")),
                new DocSeed("api/development", "本地开发", "🛠️",
                        "本地环境、项目结构、数据库迁移、测试与 Pull Request 指南。",
                        List.of("开发", "本地环境", "测试"), 3,
                        read("wiki-content/api/development.md"))
        );
    }

    private Long ensureCategory() {
        WikiCategory existing = categoryMapper.selectOne(Wrappers.<WikiCategory>lambdaQuery()
                .eq(WikiCategory::getSlug, CATEGORY_SLUG));
        if (existing != null) return existing.getId();

        WikiCategory category = new WikiCategory();
        category.setSlug(CATEGORY_SLUG);
        category.setLabel("开发文档");
        category.setIcon("🔌");
        category.setDescription("面向开发者：架构、本地开发与完整 API 参考。");
        category.setSortOrder(9);
        categoryMapper.insert(category);
        log.info("已初始化开发文档分类");
        return category.getId();
    }

    private void seedPage(DocSeed seed, Long categoryId) {
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, seed.path()));
        boolean created = page == null;
        if (!created && !isEmptyPlaceholder(page)) return;

        if (created) {
            page = new WikiPage();
            page.setPath(seed.path());
            page.setSlug(seed.path().substring(seed.path().lastIndexOf('/') + 1));
            page.setViewCount(0);
            page.setVersion(0);
        } else {
            page.setVersion((page.getVersion() == null ? 0 : page.getVersion()) + 1);
        }

        page.setCategoryId(categoryId);
        page.setCategorySlug(CATEGORY_SLUG);
        page.setTitle(seed.title());
        page.setIcon(seed.icon());
        page.setDescription(seed.description());
        page.setTags(JsonUtil.toJson(seed.tags()));
        page.setHeadings(JsonUtil.toJson(MarkdownUtil.collectHeadings(seed.content())));
        page.setContent(seed.content());
        page.setSortOrder(seed.sortOrder());
        page.setStatus("PUBLISHED");
        page.setDeleted(0);

        if (created) pageMapper.insert(page);
        else pageMapper.updateById(page);

        pageVersionService.publish(page, "MIGRATION", null, null,
                created ? "初始化公开开发文档" : "补充公开开发文档", Set.of());
        log.info("已{}开发文档 {}", created ? "创建" : "补充", seed.path());
    }

    static boolean isEmptyPlaceholder(WikiPage page) {
        int version = page.getVersion() == null ? 0 : page.getVersion();
        boolean publicPage = "PUBLISHED".equals(page.getStatus())
                && (page.getDeleted() == null || page.getDeleted() == 0);
        return publicPage && version == 0 && (page.getContent() == null || page.getContent().isBlank());
    }

    /**
     * The old api/test3 page is an unmistakable v0 upload test. Soft-delete it
     * only while it still matches that placeholder, preserving its snapshot and
     * avoiding any risk of deleting later user-authored content.
     */
    private void retireTestPlaceholder() {
        WikiPage page = pageMapper.selectOne(Wrappers.<WikiPage>lambdaQuery()
                .eq(WikiPage::getPath, "api/test3"));
        if (!isOriginalTestPlaceholder(page)) return;
        page.setDeleted(1);
        pageMapper.updateById(page);
        log.info("已隐藏开发文档分类中的旧 api/test3 占位页");
    }

    static boolean isOriginalTestPlaceholder(WikiPage page) {
        if (page == null || (page.getDeleted() != null && page.getDeleted() != 0)) return false;
        int version = page.getVersion() == null ? 0 : page.getVersion();
        return version == 0
                && "test3".equalsIgnoreCase(page.getTitle())
                && normalize(page.getContent()).equals(normalize(ORIGINAL_TEST_PLACEHOLDER));
    }

    private static String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").strip();
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("无法读取内置开发文档：" + path, e);
        }
    }

    private record DocSeed(String path, String title, String icon, String description,
                           List<String> tags, int sortOrder, String content) {}
}
