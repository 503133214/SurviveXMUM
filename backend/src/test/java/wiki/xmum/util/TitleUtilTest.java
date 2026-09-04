package wiki.xmum.util;

import org.junit.jupiter.api.Test;
import wiki.xmum.common.BizException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleUtilTest {

    @Test
    void categorySlugRejectsEverythingThatWouldBreakThePagePath() {
        // 这些字符会把 "分类/标题" 拼成打不开的路径或越级路径
        for (String bad : new String[]{"生活/../hack", "a/b", "a\\b", "a#b", "a?b", "a%b", "a..b"}) {
            assertThrows(BizException.class, () -> TitleUtil.cleanCategorySlug(bad), bad);
        }
    }

    @Test
    void blankCategorySlugMeansNoCategory() {
        assertNull(TitleUtil.cleanCategorySlug(null));
        assertNull(TitleUtil.cleanCategorySlug("   "));
    }

    @Test
    void categorySlugIsTrimmedAndLengthCapped() {
        assertEquals("生活篇", TitleUtil.cleanCategorySlug("  生活篇  "));
        BizException e = assertThrows(BizException.class,
                () -> TitleUtil.cleanCategorySlug("长".repeat(121)));
        assertTrue(e.getMessage().contains("过长"));
    }

    @Test
    void titleKeepsItsOwnRules() {
        assertEquals("交通", TitleUtil.cleanTitle(" 交通 "));
        assertThrows(BizException.class, () -> TitleUtil.cleanTitle("  "));
        assertThrows(BizException.class, () -> TitleUtil.cleanTitle("长".repeat(201)));
        assertThrows(BizException.class, () -> TitleUtil.cleanTitle("a/b"));
    }
}
