package wiki.xmum.service;

import org.junit.jupiter.api.Test;
import wiki.xmum.domain.vo.BadgeVO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeCatalogTest {

    private static Map<String, BadgeVO> byId(List<BadgeVO> badges) {
        return badges.stream().collect(Collectors.toMap(BadgeVO::getId, Function.identity()));
    }

    @Test
    void newcomerWithNothingEarnsNothing() {
        assertTrue(BadgeCatalog.evaluate(ContributorStats.EMPTY, false).isEmpty());
    }

    @Test
    void onlyTheHighestTierOfAFamilyShows() {
        // 12 篇已达成 1 和 10 两档，但不该同时挂两枚说同一件事的徽章
        var stats = new ContributorStats(12, 12, 0, 3, 0, LocalDateTime.now());
        var earned = byId(BadgeCatalog.evaluate(stats, false));
        assertTrue(earned.containsKey("ten-contributions"));
        assertFalse(earned.containsKey("first-contribution"));
        assertFalse(earned.containsKey("fifty-contributions"));
    }

    @Test
    void lockedListOffersTheNextTierWithProgress() {
        var stats = new ContributorStats(12, 12, 0, 3, 4, LocalDateTime.now());
        var all = byId(BadgeCatalog.evaluate(stats, true));

        BadgeVO nextVolume = all.get("fifty-contributions");
        assertFalse(nextVolume.getEarned());
        assertEquals(12, nextVolume.getProgress());
        assertEquals(50, nextVolume.getTarget());

        // 讨论 4/10：家族里还没达成任何一档，给出这一档的进度
        BadgeVO discuss = all.get("discussant");
        assertFalse(discuss.getEarned());
        assertEquals(4, discuss.getProgress());
    }

    @Test
    void createAndEditAreSeparateFamilies() {
        var stats = new ContributorStats(11, 1, 10, 6, 0, LocalDateTime.now());
        var earned = byId(BadgeCatalog.evaluate(stats, false));
        assertTrue(earned.containsKey("page-creator"), "新建 1 篇 → 开山者");
        assertFalse(earned.containsKey("prolific-creator"), "新建不足 10 篇");
        assertTrue(earned.containsKey("proofreader"), "修改 10 次 → 校对达人");
        assertTrue(earned.containsKey("generalist"), "参与 6 个页面 → 多面手");
    }

    @Test
    void veteranNeedsAFullYearSinceTheFirstAcceptedContribution() {
        var fresh = new ContributorStats(1, 1, 0, 1, 0, LocalDateTime.now().minusDays(300));
        assertFalse(byId(BadgeCatalog.evaluate(fresh, false)).containsKey("veteran"));

        var old = new ContributorStats(1, 1, 0, 1, 0, LocalDateTime.now().minusDays(400));
        assertTrue(byId(BadgeCatalog.evaluate(old, false)).containsKey("veteran"));
    }

    @Test
    void progressNeverExceedsTheTarget() {
        var stats = new ContributorStats(80, 80, 0, 40, 99, LocalDateTime.now().minusDays(400));
        for (BadgeVO b : BadgeCatalog.evaluate(stats, true)) {
            assertTrue(b.getProgress() <= b.getTarget(), b.getId());
        }
    }

    @Test
    void nullStatsAreTreatedAsEmpty() {
        assertTrue(BadgeCatalog.evaluate(null, false).isEmpty());
    }
}
