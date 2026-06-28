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
}
