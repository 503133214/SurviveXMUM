package wiki.xmum.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 markdown 解析：抽取 h2-h4 标题（用于搜索/TOC 元数据），与前端 generate-manifest 行为一致。
 */
public final class MarkdownUtil {
    private MarkdownUtil() {}

    private static final Pattern FENCE = Pattern.compile("^\\s*```");
    private static final Pattern HEADING = Pattern.compile("^(#{2,4})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern H1 = Pattern.compile("^#\\s+(.+?)\\s*#*\\s*$");

    public static List<String> collectHeadings(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) return out;
        boolean inFence = false;
        for (String line : body.split("\\r?\\n")) {
            if (FENCE.matcher(line).find()) { inFence = !inFence; continue; }
            if (inFence) continue;
            Matcher m = HEADING.matcher(line);
            if (m.matches()) out.add(m.group(2).trim());
        }
        return out;
    }

    public static String firstH1(String body) {
        if (body == null) return null;
        boolean inFence = false;
        for (String line : body.split("\\r?\\n")) {
            if (FENCE.matcher(line).find()) { inFence = !inFence; continue; }
            if (inFence) continue;
            Matcher m = H1.matcher(line);
            if (m.matches()) return m.group(1).trim();
        }
        return null;
    }

    /**
     * 从正文提取首个普通段落作为简介（投稿未填简介时自动生成）。
     * 跳过代码块、标题、图片、HTML、表格行，去掉行内 markdown 标记，最长 maxLen 字。
     */
    public static String extractSummary(String body, int maxLen) {
        if (body == null) return null;
        boolean inFence = false;
        List<String> para = new ArrayList<>();
        for (String line : body.split("\\r?\\n")) {
            if (FENCE.matcher(line).find()) { inFence = !inFence; continue; }
            if (inFence) continue;
            String t = line.trim();
            if (t.startsWith(">")) t = t.substring(1).trim(); // 引用块也算正文
            if (t.isEmpty()) {
                if (!para.isEmpty()) break; // 段落结束
                continue;
            }
            // 标题 / 图片行 / HTML 行 / 表格行 / 分隔线不作为简介
            if (t.startsWith("#") || t.startsWith("![") || t.startsWith("<")
                    || t.startsWith("|") || t.matches("^[-*_]{3,}$")) {
                if (!para.isEmpty()) break;
                continue;
            }
            para.add(t.replaceFirst("^([-*+]|\\d+\\.)\\s+", "")); // 列表项去掉标记
        }
        if (para.isEmpty()) return null;
        String s = String.join(" ", para)
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")   // 图片
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1") // 链接留文字
                .replaceAll("<[^>]+>", "")                        // 行内 HTML
                .replaceAll("[*_`~]+", "")                         // 加粗/斜体/代码标记
                .replaceAll("\\s+", " ")
                .trim();
        if (s.isEmpty()) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }
}
