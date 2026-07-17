package wiki.xmum.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.domain.po.WikiCategory;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;
import wiki.xmum.service.PageVersionService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperDocsSeederTest {

    @Mock private WikiCategoryMapper categoryMapper;
    @Mock private WikiPageMapper pageMapper;
    @Mock private PageVersionService pageVersionService;

    @Test
    void fillsKnownEmptyPlaceholderWithoutOverwritingExistingDocs() {
        WikiCategory category = category(9L);
        WikiPage overview = page(1066L, "api/api-overview", "", 0);
        WikiPage endpoints = page(1067L, "api/endpoints", "# 管理员已经写过", 4);
        WikiPage development = page(1068L, "api/development", "# 自定义开发文档", 2);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(pageMapper.selectOne(any())).thenReturn(overview, endpoints, development, null);

        seeder().run();

        assertEquals("API 概览", overview.getTitle());
        assertEquals(1, overview.getVersion());
        assertTrue(overview.getContent().startsWith("# API 概览"));
        assertEquals("# 管理员已经写过", endpoints.getContent());
        verify(pageMapper).updateById(overview);
        verify(pageMapper, never()).insert(any(WikiPage.class));
        verify(pageVersionService).publish(eq(overview), eq("MIGRATION"), isNull(), isNull(),
                eq("补充公开开发文档"), eq(Set.of()));
    }

    @Test
    void createsCategoryAndAllDocsOnFreshDatabase() {
        when(categoryMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            WikiCategory category = invocation.getArgument(0);
            category.setId(9L);
            return 1;
        }).when(categoryMapper).insert(any(WikiCategory.class));
        when(pageMapper.selectOne(any())).thenReturn(null, null, null, null);
        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            WikiPage page = invocation.getArgument(0);
            page.setId(ids.incrementAndGet());
            return 1;
        }).when(pageMapper).insert(any(WikiPage.class));

        seeder().run();

        ArgumentCaptor<WikiPage> pages = ArgumentCaptor.forClass(WikiPage.class);
        verify(pageMapper, times(3)).insert(pages.capture());
        List<String> paths = pages.getAllValues().stream().map(WikiPage::getPath).toList();
        assertEquals(List.of("api/api-overview", "api/endpoints", "api/development"), paths);
        assertTrue(pages.getAllValues().stream().allMatch(p -> p.getContent() != null && !p.getContent().isBlank()));
        assertEquals(Set.of("API 概览", "接口参考", "本地开发"), pages.getAllValues().stream()
                .map(WikiPage::getTitle).collect(Collectors.toSet()));
        verify(pageVersionService, times(3)).publish(any(WikiPage.class), eq("MIGRATION"),
                isNull(), isNull(), eq("初始化公开开发文档"), eq(Set.of()));
    }

    @Test
    void retiresOnlyTheUnchangedTestPlaceholder() {
        WikiCategory category = category(9L);
        WikiPage overview = page(1066L, "api/api-overview", "# 已有概览", 1);
        WikiPage endpoints = page(1067L, "api/endpoints", "# 已有接口", 1);
        WikiPage development = page(1068L, "api/development", "# 已有开发文档", 1);
        WikiPage placeholder = page(2000L, "api/test3", originalTestContent(), 0);
        placeholder.setTitle("test3");
        placeholder.setDeleted(0);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(pageMapper.selectOne(any())).thenReturn(overview, endpoints, development, placeholder);

        seeder().run();

        assertEquals(1, placeholder.getDeleted());
        verify(pageMapper).updateById(placeholder);
        verify(pageMapper, times(1)).updateById(any(WikiPage.class));
        verifyNoInteractions(pageVersionService);
    }

    @Test
    void recognizesOnlyTheExactOriginalTestPlaceholder() {
        WikiPage exact = page(2000L, "api/test3", originalTestContent(), 0);
        exact.setTitle("test3");
        assertTrue(DeveloperDocsSeeder.isOriginalTestPlaceholder(exact));

        WikiPage edited = page(2000L, "api/test3", originalTestContent() + "\n新增说明", 0);
        edited.setTitle("test3");
        assertFalse(DeveloperDocsSeeder.isOriginalTestPlaceholder(edited));

        WikiPage newer = page(2000L, "api/test3", originalTestContent(), 1);
        newer.setTitle("test3");
        assertFalse(DeveloperDocsSeeder.isOriginalTestPlaceholder(newer));

        WikiPage deleted = page(2000L, "api/test3", originalTestContent(), 0);
        deleted.setTitle("test3");
        deleted.setDeleted(1);
        assertFalse(DeveloperDocsSeeder.isOriginalTestPlaceholder(deleted));
    }

    @Test
    void doesNotRepublishDraftOrDeletedEmptyPage() {
        WikiPage draft = page(1066L, "api/api-overview", "", 0);
        draft.setStatus("DRAFT");
        assertFalse(DeveloperDocsSeeder.isEmptyPlaceholder(draft));

        WikiPage deleted = page(1066L, "api/api-overview", "", 0);
        deleted.setDeleted(1);
        assertFalse(DeveloperDocsSeeder.isEmptyPlaceholder(deleted));

        WikiPage published = page(1066L, "api/api-overview", "", 0);
        assertTrue(DeveloperDocsSeeder.isEmptyPlaceholder(published));
    }

    @Test
    void bundledHashUpgradeOnlyMatchesExactPreviousContent() {
        Set<String> knownHashes = Set.of(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");

        assertTrue(DeveloperDocsSeeder.matchesBundledHash("abc", knownHashes));
        assertFalse(DeveloperDocsSeeder.matchesBundledHash("abc\n", knownHashes));
        assertFalse(DeveloperDocsSeeder.matchesBundledHash("管理员自行修改", knownHashes));
        assertFalse(DeveloperDocsSeeder.matchesBundledHash(null, knownHashes));
    }

    @Test
    void upgradesExactBundledContentOnceAndPreservesAdministratorMetadata() {
        WikiCategory category = category(9L);
        WikiPage overview = page(1066L, "api/api-overview", "# 自定义概览", 2);
        WikiPage endpoints = page(1067L, "api/endpoints", "abc", 4);
        endpoints.setTitle("管理员自定义标题");
        endpoints.setIcon("🧪");
        endpoints.setDescription("管理员自定义简介");
        endpoints.setTags("[\"custom\"]");
        endpoints.setCategoryId(88L);
        endpoints.setCategorySlug("custom-category");
        endpoints.setSortOrder(77);
        WikiPage development = page(1068L, "api/development", "# 自定义开发文档", 3);
        when(categoryMapper.selectOne(any())).thenReturn(category);
        when(pageMapper.selectOne(any())).thenReturn(
                overview, endpoints, development, null,
                overview, endpoints, development, null);

        DeveloperDocsSeeder managedSeeder = seeder(Map.of(
                "api/endpoints", Set.of(
                        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")));
        managedSeeder.run();
        managedSeeder.run();

        assertEquals(5, endpoints.getVersion());
        assertTrue(endpoints.getContent().startsWith("# 接口参考"));
        assertEquals("管理员自定义标题", endpoints.getTitle());
        assertEquals("🧪", endpoints.getIcon());
        assertEquals("管理员自定义简介", endpoints.getDescription());
        assertEquals("[\"custom\"]", endpoints.getTags());
        assertEquals(88L, endpoints.getCategoryId());
        assertEquals("custom-category", endpoints.getCategorySlug());
        assertEquals(77, endpoints.getSortOrder());
        verify(pageMapper, times(1)).updateById(endpoints);
        verify(pageVersionService, times(1)).publish(eq(endpoints), eq("MIGRATION"),
                isNull(), isNull(), eq("更新内置开发文档"), eq(Set.of()));
    }

    @Test
    void managedUpgradeNeverRepublishesDraftOrDeletedPage() {
        Set<String> knownHashes = Set.of(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        WikiPage published = page(1L, "api/endpoints", "abc", 1);
        WikiPage draft = page(2L, "api/endpoints", "abc", 1);
        draft.setStatus("DRAFT");
        WikiPage deleted = page(3L, "api/endpoints", "abc", 1);
        deleted.setDeleted(1);

        assertTrue(DeveloperDocsSeeder.isManagedUpgradeCandidate(published, knownHashes));
        assertFalse(DeveloperDocsSeeder.isManagedUpgradeCandidate(draft, knownHashes));
        assertFalse(DeveloperDocsSeeder.isManagedUpgradeCandidate(deleted, knownHashes));
    }

    private DeveloperDocsSeeder seeder() {
        return new DeveloperDocsSeeder(categoryMapper, pageMapper, pageVersionService);
    }

    private DeveloperDocsSeeder seeder(Map<String, Set<String>> replaceableBundledHashes) {
        return new DeveloperDocsSeeder(categoryMapper, pageMapper, pageVersionService,
                replaceableBundledHashes);
    }

    private static WikiCategory category(Long id) {
        WikiCategory category = new WikiCategory();
        category.setId(id);
        category.setSlug("api");
        return category;
    }

    private static WikiPage page(Long id, String path, String content, int version) {
        WikiPage page = new WikiPage();
        page.setId(id);
        page.setPath(path);
        page.setSlug(path.substring(path.lastIndexOf('/') + 1));
        page.setTitle(path);
        page.setContent(content);
        page.setVersion(version);
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        page.setViewCount(0);
        return page;
    }

    private static String originalTestContent() {
        return """
                # test3
                这是一篇test3文章
                ![_](https://surivivexmum.wiki/wiki/images/2026/06/61c949cf-c5db-49ad-9680-a84ae7ba1538.jpg =267x)

                hello
                ![Messi in 2010_](https://surivivexmum.wiki/wiki/images/2026/06/03d35a0e-d410-462e-9e0e-fe6e5b22727f.jpg =276x)

                你好
                """;
    }
}
