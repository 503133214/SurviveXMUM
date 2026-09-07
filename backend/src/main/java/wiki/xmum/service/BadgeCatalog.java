package wiki.xmum.service;

import wiki.xmum.domain.vo.BadgeVO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * 徽章目录。全部依据 {@link ContributorStats} 现算，改规则只需改这里。
 *
 * <p>同一「家族」（如投稿量的 1 / 10 / 50）只展示已达成的最高一档，
 * 否则一个老贡献者会挂着三枚说同一件事的徽章。未达成时展示家族里的下一档并附进度。
 */
public final class BadgeCatalog {

    private BadgeCatalog() {}

    private record Def(String id, String icon, String name, String description,
                       String family, int target, ToIntFunction<ContributorStats> value) {}

    private static final List<Def> DEFS = List.of(
            new Def("first-contribution", "🌱", "初次贡献", "第一篇投稿被采纳",
                    "volume", 1, ContributorStats::approved),
            new Def("ten-contributions", "📗", "十篇达成", "累计 10 篇投稿被采纳",
                    "volume", 10, ContributorStats::approved),
            new Def("fifty-contributions", "📚", "五十篇达成", "累计 50 篇投稿被采纳",
                    "volume", 50, ContributorStats::approved),

            new Def("page-creator", "✍️", "开山者", "新建的页面被采纳",
                    "create", 1, ContributorStats::created),
            new Def("prolific-creator", "🏗️", "建站主力", "新建 10 个页面被采纳",
                    "create", 10, ContributorStats::created),

            new Def("proofreader", "🔍", "校对达人", "10 次对已有页面的修改被采纳",
                    "edit", 10, ContributorStats::edited),

            new Def("generalist", "🧭", "多面手", "参与过 5 个不同页面",
                    "breadth", 5, ContributorStats::pages),

            new Def("discussant", "💬", "讨论活跃", "在讨论区发表 10 条讨论",
                    "discuss", 10, ContributorStats::comments),

            new Def("veteran", "⏳", "元老", "第一篇投稿被采纳已满一年",
                    "tenure", 1, BadgeCatalog::tenureValue)
    );

    private static int tenureValue(ContributorStats s) {
        LocalDateTime first = s.firstContributionAt();
        if (first == null) return 0;
        return Duration.between(first, LocalDateTime.now()).toDays() >= 365 ? 1 : 0;
    }

    /**
     * @param includeLocked true 时额外给出每个家族尚未达成的下一档（带进度），用于个人主页；
     *                      false 只返回已获得的，用于贡献榜这类空间紧张的地方。
     */
    public static List<BadgeVO> evaluate(ContributorStats stats, boolean includeLocked) {
        ContributorStats s = stats == null ? ContributorStats.EMPTY : stats;
        Map<String, BadgeVO> earnedByFamily = new LinkedHashMap<>();
        Map<String, BadgeVO> nextByFamily = new LinkedHashMap<>();

        for (Def def : DEFS) {
            int value = def.value().applyAsInt(s);
            if (value >= def.target()) {
                // 家族内按定义顺序递增，后达成的一档自然覆盖前一档
                earnedByFamily.put(def.family(), toVO(def, value, true));
            } else if (!nextByFamily.containsKey(def.family())) {
                nextByFamily.put(def.family(), toVO(def, value, false));
            }
        }

        List<BadgeVO> out = new ArrayList<>(earnedByFamily.values());
        if (includeLocked) out.addAll(nextByFamily.values());
        return out;
    }

    private static BadgeVO toVO(Def def, int value, boolean earned) {
        BadgeVO v = new BadgeVO();
        v.setId(def.id());
        v.setIcon(def.icon());
        v.setName(def.name());
        v.setDescription(def.description());
        v.setEarned(earned);
        v.setProgress(Math.min(value, def.target()));
        v.setTarget(def.target());
        return v;
    }
}
