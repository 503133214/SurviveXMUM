package wiki.xmum.service;

import java.time.LocalDateTime;

/**
 * 计算徽章所需的全部口径，全部来自已通过投稿与讨论，不新增存储。
 *
 * @param approved 已通过投稿总数
 * @param created  其中新建页面的数量
 * @param edited   其中修改已有页面的数量
 * @param pages    参与过的不同页面数
 * @param comments 发表过的可见讨论数
 * @param firstContributionAt 第一次投稿被通过的时间，用于资历徽章
 */
public record ContributorStats(int approved, int created, int edited, int pages, int comments,
                               LocalDateTime firstContributionAt) {

    public static final ContributorStats EMPTY = new ContributorStats(0, 0, 0, 0, 0, null);
}
