package wiki.xmum.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import wiki.xmum.domain.po.WikiPage;
import wiki.xmum.mapper.WikiCategoryMapper;
import wiki.xmum.mapper.WikiPageMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiContentServiceTest {

    @Mock private WikiCategoryMapper categoryMapper;
    @Mock private WikiPageMapper pageMapper;

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void trackedPageViewExplicitlyPreservesArticleTimestamp() {
        WikiPage page = new WikiPage();
        page.setId(10L);
        page.setPath("生活/交通");
        page.setTitle("交通");
        page.setStatus("PUBLISHED");
        page.setDeleted(0);
        page.setViewCount(7);
        when(pageMapper.selectOne(any())).thenReturn(page);

        new WikiContentService(categoryMapper, pageMapper).getPage(page.getPath(), true);

        ArgumentCaptor<LambdaUpdateWrapper> updateCaptor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(pageMapper).update(isNull(), updateCaptor.capture());
        String sqlSet = updateCaptor.getValue().getSqlSet()
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .toLowerCase();
        assertTrue(sqlSet.contains("view_count = view_count + 1"));
        assertTrue(sqlSet.contains("updated_at = updated_at"));
    }
}
