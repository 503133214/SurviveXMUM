package wiki.xmum.util;

import wiki.xmum.common.BizException;

import java.util.regex.Pattern;

/**
 * 标题会拼进页面 path（分类/标题）并作为前端路由段，
 * 这里统一挡掉会破坏路径/路由的字符（投稿与管理端建页共用）。
 */
public final class TitleUtil {
    private TitleUtil() {}

    private static final Pattern ILLEGAL = Pattern.compile("[/\\\\#?%]|\\.\\.|[\\x00-\\x1f]");

    /**
     * 分类 slug 同样直接拼进 path 与前端路由段，规则与标题一致（长度上限不同）。
     * 空/空白返回 null，表示“不归属任何分类”。
     */
    public static String cleanCategorySlug(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String t = raw.trim();
        if (t.length() > 120) throw new BizException("分类标识过长（最多 120 字）");
        if (ILLEGAL.matcher(t).find()) {
            throw new BizException("分类标识不能包含 / \\ # ? % 或 .. 等字符");
        }
        return t;
    }

    public static String cleanTitle(String raw) {
        if (raw == null || raw.isBlank()) throw new BizException("请填写标题");
        String t = raw.trim();
        if (t.length() > 200) throw new BizException("标题过长（最多 200 字）");
        if (ILLEGAL.matcher(t).find()) {
            throw new BizException("标题不能包含 / \\ # ? % 或 .. 等字符");
        }
        return t;
    }
}
