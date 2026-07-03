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
