<template>
  <main class="contrib-page">
    <section class="community-hero">
      <div class="hero-copy">
        <p class="eyebrow">XMUM WIKI COMMUNITY</p>
        <h1>贡献者<span>与致谢</span></h1>
        <p class="hero-lead">
          每一篇被修正的文档、每一段被补充的经验，都让后来的人少走一点弯路。
          谢谢每一位认真留下答案的人。
        </p>

        <div class="community-stats" aria-label="社区贡献数据">
          <div class="stat">
            <strong><AnimatedNumber :to="contributors.length" /></strong>
            <span>位公开贡献者</span>
          </div>
          <div class="stat">
            <strong><AnimatedNumber :to="totalContributions" /></strong>
            <span>篇已通过投稿</span>
          </div>
          <div class="stat">
            <strong><AnimatedNumber :to="wall.length" /></strong>
            <span>份支持与致谢</span>
          </div>
        </div>
      </div>

      <div class="community-mark" aria-hidden="true">
        <span class="mark-ring ring-one"></span>
        <span class="mark-ring ring-two"></span>
        <span class="mark-core">谢</span>
        <div v-if="topContributors.length" class="mark-avatars">
          <el-avatar
            v-for="c in topContributors"
            :key="c.userId"
            :size="42"
            :src="c.avatar || undefined"
          >
            {{ initial(c.displayName) }}
          </el-avatar>
        </div>
      </div>
    </section>

    <section v-if="loadingWall || wall.length" class="cp-section thanks-section" v-reveal>
      <div class="section-head">
        <span class="section-index">01</span>
        <div>
          <p class="section-kicker">WITH GRATITUDE</p>
          <h2>赞助与致谢</h2>
          <p>这些名字以不同的方式，为这份指南提供过帮助。</p>
        </div>
      </div>

      <div v-if="loadingWall" class="wall-grid skeleton-grid" aria-label="正在加载致谢名单">
        <el-skeleton v-for="i in 4" :key="i" animated>
          <template #template>
            <div class="skeleton-card">
              <el-skeleton-item variant="circle" class="skeleton-avatar" />
              <el-skeleton-item variant="text" style="width: 58%" />
              <el-skeleton-item variant="text" style="width: 76%" />
            </div>
          </template>
        </el-skeleton>
      </div>

      <div v-else class="wall-groups">
        <div v-for="group in wallGroups" :key="group.category || '_'" class="wall-group">
          <div class="wall-group-head">
            <h3>{{ group.category || '特别致谢' }}</h3>
            <span>{{ group.items.length }} 位</span>
          </div>
          <div class="wall-grid">
            <component
              :is="item.link ? 'a' : 'article'"
              v-for="(item, index) in group.items"
              :key="item.id"
              class="wall-card"
              :href="item.link || undefined"
              :target="item.link ? '_blank' : undefined"
              :aria-label="item.link ? `${item.name}，在新窗口打开链接` : undefined"
              rel="noopener noreferrer"
              v-reveal="{ delay: Math.min(index, 7) * 55 }"
            >
              <span v-if="item.link" class="external-mark" aria-hidden="true">↗</span>
              <div class="wall-avatar">
                <el-avatar :size="62" :src="item.avatar || undefined">
                  {{ initial(item.name) }}
                </el-avatar>
              </div>
              <div class="wall-name">{{ item.name }}</div>
              <div v-if="item.description" class="wall-desc">{{ item.description }}</div>
              <div v-else class="wall-desc wall-desc-muted">感谢你的支持</div>
            </component>
          </div>
        </div>
      </div>
    </section>

    <section class="cp-section leaderboard-section" v-reveal>
      <div class="section-head">
        <span class="section-index">02</span>
        <div>
          <p class="section-kicker">CONTRIBUTORS</p>
          <h2>社区贡献榜</h2>
          <p>按已通过的投稿数量排名；每一次修订都被认真记住。</p>
        </div>
      </div>

      <div v-if="loadingContributors" class="leaderboard-loading">
        <el-skeleton :rows="6" animated />
      </div>
      <el-empty v-else-if="!contributors.length" description="暂无贡献数据" />
      <template v-else>
        <div class="podium-grid">
          <router-link
            v-for="(contributor, index) in topContributors"
            :key="contributor.userId"
            class="podium-card"
            :class="`rank-${index + 1}`"
            :to="`/contributors/${contributor.userId}`"
            v-reveal="{ delay: index * 75 }"
          >
            <span class="rank-badge">{{ index + 1 }}</span>
            <div class="podium-avatar">
              <el-avatar :size="68" :src="contributor.avatar || undefined">
                {{ initial(contributor.displayName) }}
              </el-avatar>
              <span v-if="index === 0" class="crown" aria-hidden="true">✦</span>
            </div>
            <div class="podium-copy">
              <strong>{{ contributor.displayName }}</strong>
              <span>{{ contributor.count }} 篇贡献</span>
            </div>
            <span class="card-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>

        <div v-if="remainingContributors.length" class="leader-list">
          <router-link
            v-for="(contributor, index) in remainingContributors"
            :key="contributor.userId"
            class="leader-row"
            :to="`/contributors/${contributor.userId}`"
          >
            <span class="leader-rank">{{ index + 4 }}</span>
            <el-avatar :size="40" :src="contributor.avatar || undefined">
              {{ initial(contributor.displayName) }}
            </el-avatar>
            <span class="leader-name">{{ contributor.displayName }}</span>
            <span class="leader-count">{{ contributor.count }} 篇</span>
            <span class="leader-arrow" aria-hidden="true">→</span>
          </router-link>
        </div>
      </template>
    </section>

    <footer class="gratitude-note" v-reveal>
      <span class="gratitude-symbol" aria-hidden="true">✦</span>
      <p>知识因为分享而留下，社区因为每一个你而完整。</p>
      <router-link to="/docs/贡献指南">加入共建 <span aria-hidden="true">→</span></router-link>
    </footer>
  </main>
</template>

<script>
import AnimatedNumber from '@/components/AnimatedNumber.vue'
import { getContributors, getWall } from '@/net/index.js'

export default {
  name: 'ContributorsPage',
  components: { AnimatedNumber },
  data() {
    return {
      contributors: [],
      wall: [],
      loadingContributors: true,
      loadingWall: true,
    }
  },
  computed: {
    totalContributions() {
      return this.contributors.reduce((sum, item) => sum + (Number(item.count) || 0), 0)
    },
    topContributors() {
      return this.contributors.slice(0, 3)
    },
    remainingContributors() {
      return this.contributors.slice(3)
    },
    wallGroups() {
      const groups = []
      const indexes = {}
      for (const item of this.wall) {
        const category = item.category || ''
        if (!(category in indexes)) {
          indexes[category] = groups.length
          groups.push({ category, items: [] })
        }
        groups[indexes[category]].items.push(item)
      }
      return groups
    },
  },
  methods: {
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase()
    },
  },
  mounted() {
    getContributors(
      (data) => {
        this.contributors = data || []
        this.loadingContributors = false
      },
      () => {
        this.contributors = []
        this.loadingContributors = false
      },
    )
    getWall(
      (data) => {
        this.wall = data || []
        this.loadingWall = false
      },
      () => {
        this.wall = []
        this.loadingWall = false
      },
    )
  },
}
</script>

<style scoped>
.contrib-page {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 44px 28px 80px;
}

.community-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(280px, .55fr);
  align-items: center;
  min-height: 420px;
  overflow: hidden;
  padding: clamp(42px, 7vw, 76px);
  border: 1px solid var(--border);
  border-radius: 32px;
  background:
    radial-gradient(circle at 88% 20%, color-mix(in srgb, var(--text-primary) 8%, transparent), transparent 28%),
    linear-gradient(145deg, var(--bg-surface), var(--bg-subtle));
}
.community-hero::before {
  content: "";
  position: absolute;
  right: -8%;
  bottom: -45%;
  width: 420px;
  height: 420px;
  border: 1px solid var(--border);
  border-radius: 50%;
  opacity: .72;
}
.hero-copy { position: relative; z-index: 2; max-width: 680px; }
.eyebrow,
.section-kicker {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .16em;
}
.community-hero h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(3rem, 6.8vw, 5.8rem);
  font-weight: 850;
  letter-spacing: -.055em;
  line-height: .96;
}
.community-hero h1 span {
  display: block;
  color: var(--text-muted);
  font-weight: 620;
}
.hero-lead {
  max-width: 610px;
  margin: 28px 0 0;
  color: var(--text-secondary);
  font-size: clamp(15px, 1.7vw, 18px);
  line-height: 1.75;
}
.community-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 28px;
  margin-top: 36px;
}
.stat { display: flex; flex-direction: column; min-width: 104px; }
.stat strong {
  color: var(--text-primary);
  font-size: 24px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.stat span { margin-top: 4px; color: var(--text-muted); font-size: 12px; }

.community-mark {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  justify-self: center;
  width: min(23vw, 250px);
  min-width: 200px;
  aspect-ratio: 1;
}
.mark-ring {
  position: absolute;
  inset: 4%;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  animation: orbit 18s linear infinite;
}
.ring-one::before,
.ring-two::before {
  content: "";
  position: absolute;
  top: 10%;
  left: 14%;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--text-primary);
  box-shadow: 0 0 0 7px var(--bg-surface);
}
.ring-two {
  inset: 20%;
  border-style: dashed;
  animation-direction: reverse;
  animation-duration: 13s;
}
.ring-two::before { top: auto; right: 7%; bottom: 14%; left: auto; width: 6px; height: 6px; }
.mark-core {
  display: grid;
  place-items: center;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--text-primary);
  color: var(--bg-page);
  font-size: 35px;
  font-weight: 800;
  box-shadow: 0 18px 50px color-mix(in srgb, var(--text-primary) 24%, transparent);
  animation: mark-float 4.8s var(--ease-out) infinite;
}
.mark-avatars {
  position: absolute;
  bottom: -2px;
  display: flex;
  padding: 5px 8px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--glass-bg);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(12px);
}
.mark-avatars :deep(.el-avatar) {
  margin-left: -9px;
  border: 3px solid var(--bg-surface);
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 700;
}
.mark-avatars :deep(.el-avatar:first-child) { margin-left: 0; }

.cp-section { padding-top: 82px; }
.section-head {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  margin-bottom: 30px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}
.section-index {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}
.section-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: clamp(1.65rem, 3vw, 2.35rem);
  font-weight: 800;
  letter-spacing: -.03em;
}
.section-head p:last-child {
  margin: 7px 0 0;
  color: var(--text-secondary);
  font-size: 14px;
}

.wall-groups { display: flex; flex-direction: column; gap: 32px; }
.wall-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 13px;
}
.wall-group-head h3 { margin: 0; color: var(--text-primary); font-size: 14px; font-weight: 750; }
.wall-group-head span { color: var(--text-muted); font-size: 12px; }
.wall-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.wall-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 205px;
  flex-direction: column;
  align-items: flex-start;
  padding: 22px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--bg-surface);
  color: var(--text-primary);
  text-decoration: none;
  transition:
    transform .35s var(--ease-out),
    border-color .25s ease,
    box-shadow .35s var(--ease-out);
}
.wall-card::after {
  content: "";
  position: absolute;
  right: -30px;
  bottom: -42px;
  width: 110px;
  height: 110px;
  border: 1px solid var(--border);
  border-radius: 50%;
  transition: transform .45s var(--ease-out);
}
a.wall-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  text-decoration: none;
  transform: translateY(-5px);
}
a.wall-card:hover::after { transform: scale(1.35); }
.external-mark {
  position: absolute;
  top: 16px;
  right: 18px;
  color: var(--text-muted);
  font-size: 17px;
  transition: transform .3s var(--ease-out), color .2s ease;
}
a.wall-card:hover .external-mark { color: var(--text-primary); transform: translate(2px, -2px); }
.wall-avatar { margin-bottom: 18px; }
.wall-avatar :deep(.el-avatar) {
  border: 1px solid var(--border);
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-size: 20px;
  font-weight: 800;
}
.wall-name {
  max-width: 100%;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 760;
  line-height: 1.35;
  overflow-wrap: anywhere;
}
.wall-desc {
  position: relative;
  z-index: 1;
  display: -webkit-box;
  margin-top: 7px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12.5px;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}
.wall-desc-muted { color: var(--text-muted); }
.skeleton-card {
  display: flex;
  min-height: 205px;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 24px;
  border: 1px solid var(--border);
  border-radius: 20px;
}
.skeleton-avatar { width: 62px !important; height: 62px !important; }

.podium-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.podium-card {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 240px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 28px 22px 24px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 22px;
  background: var(--bg-surface);
  color: var(--text-primary);
  text-align: center;
  text-decoration: none;
  transition: transform .35s var(--ease-out), border-color .2s ease, box-shadow .3s ease;
}
.podium-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--shadow-md);
  text-decoration: none;
  transform: translateY(-5px);
}
.podium-card.rank-1 {
  background:
    radial-gradient(circle at 50% 0%, rgba(216, 169, 77, .2), transparent 42%),
    var(--bg-surface);
}
.rank-badge {
  position: absolute;
  top: 16px;
  left: 17px;
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 9px;
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 850;
}
.rank-1 .rank-badge { background: #d8a94d; color: #201600; }
.rank-2 .rank-badge { background: #c9cbd0; color: #24252a; }
.rank-3 .rank-badge { background: #c78962; color: #271207; }
.podium-avatar { position: relative; margin-bottom: 16px; }
.podium-avatar :deep(.el-avatar) {
  border: 3px solid var(--bg-surface);
  background: var(--accent);
  color: var(--accent-contrast);
  box-shadow: 0 0 0 1px var(--border-strong);
  font-size: 22px;
  font-weight: 800;
}
.crown {
  position: absolute;
  top: -13px;
  right: -8px;
  display: grid;
  place-items: center;
  width: 27px;
  height: 27px;
  border-radius: 50%;
  background: #d8a94d;
  color: #201600;
  font-size: 13px;
  box-shadow: 0 4px 12px rgba(140, 96, 20, .22);
}
.podium-copy { display: flex; min-width: 0; flex-direction: column; }
.podium-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.podium-copy span { margin-top: 5px; color: var(--text-muted); font-size: 12.5px; }
.card-arrow {
  position: absolute;
  right: 18px;
  bottom: 15px;
  color: var(--text-muted);
  opacity: 0;
  transform: translateX(-5px);
  transition: opacity .2s ease, transform .3s var(--ease-out);
}
.podium-card:hover .card-arrow { opacity: 1; transform: none; }

.leader-list {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--bg-surface);
}
.leader-row {
  display: grid;
  grid-template-columns: 38px 40px minmax(0, 1fr) auto 20px;
  align-items: center;
  gap: 14px;
  min-height: 72px;
  padding: 11px 18px;
  color: var(--text-primary);
  text-decoration: none;
  transition: background .2s ease, padding-left .3s var(--ease-out);
}
.leader-row + .leader-row { border-top: 1px solid var(--border); }
.leader-row:hover { padding-left: 24px; background: var(--bg-subtle); text-decoration: none; }
.leader-rank { color: var(--text-muted); font-size: 12px; font-weight: 800; text-align: center; }
.leader-row :deep(.el-avatar) {
  background: var(--bg-hover);
  color: var(--text-primary);
  font-weight: 750;
}
.leader-name { overflow: hidden; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.leader-count { color: var(--text-secondary); font-size: 13px; white-space: nowrap; }
.leader-arrow { color: var(--text-muted); opacity: 0; transition: opacity .2s ease; }
.leader-row:hover .leader-arrow { opacity: 1; }
.leaderboard-loading { padding: 10px 4px; }

.gratitude-note {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 82px;
  padding: 30px 0 0;
  border-top: 1px solid var(--border);
}
.gratitude-symbol {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 50%;
  background: var(--text-primary);
  color: var(--bg-page);
  font-size: 13px;
}
.gratitude-note p { flex: 1; margin: 0; color: var(--text-secondary); font-size: 14px; }
.gratitude-note a { color: var(--text-primary); font-size: 14px; font-weight: 750; white-space: nowrap; }
.gratitude-note a:hover { text-decoration: none; opacity: .65; }

@keyframes orbit {
  to { transform: rotate(360deg); }
}
@keyframes mark-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-9px); }
}

@media (max-width: 900px) {
  .community-hero { grid-template-columns: minmax(0, 1fr) 220px; padding: 48px; }
  .wall-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 700px) {
  .contrib-page { padding: 18px 14px 54px; }
  .community-hero {
    display: block;
    min-height: 0;
    padding: 40px 28px 34px;
    border-radius: 24px;
  }
  .community-hero h1 { font-size: clamp(2.7rem, 15vw, 4.1rem); }
  .hero-lead { margin-top: 22px; font-size: 14px; }
  .community-mark {
    position: absolute;
    top: 28px;
    right: 22px;
    width: 92px;
    min-width: 0;
    opacity: .32;
  }
  .mark-core { width: 42px; height: 42px; font-size: 17px; box-shadow: none; }
  .mark-avatars { display: none; }
  .community-stats { gap: 18px; margin-top: 28px; }
  .stat { min-width: 82px; }
  .stat strong { font-size: 20px; }
  .cp-section { padding-top: 58px; }
  .section-head { grid-template-columns: 40px minmax(0, 1fr); gap: 12px; margin-bottom: 22px; padding-bottom: 18px; }
  .section-index { width: 34px; height: 34px; font-size: 10px; }
  .section-kicker { margin-bottom: 7px; font-size: 9px; }
  .section-head p:last-child { font-size: 12.5px; line-height: 1.55; }
  .wall-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .wall-card { min-height: 190px; padding: 18px 15px; border-radius: 17px; }
  .wall-avatar { margin-bottom: 14px; }
  .wall-avatar :deep(.el-avatar) { --el-avatar-size: 54px !important; }
  .wall-name { font-size: 14px; }
  .wall-desc { font-size: 11.5px; }
  .podium-grid { grid-template-columns: 1fr; gap: 10px; }
  .podium-card {
    min-height: 0;
    flex-direction: row;
    justify-content: flex-start;
    gap: 14px;
    padding: 16px 46px 16px 54px;
    text-align: left;
  }
  .podium-avatar { margin: 0; }
  .podium-avatar :deep(.el-avatar) { --el-avatar-size: 52px !important; }
  .podium-copy { text-align: left; }
  .crown { top: -8px; right: -7px; width: 22px; height: 22px; font-size: 10px; }
  .rank-badge { top: 50%; left: 14px; width: 27px; height: 27px; transform: translateY(-50%); }
  .card-arrow { top: 50%; right: 16px; bottom: auto; opacity: 1; transform: translateY(-50%); }
  .leader-row {
    grid-template-columns: 28px 36px minmax(0, 1fr) auto;
    gap: 10px;
    min-height: 64px;
    padding: 10px 13px;
  }
  .leader-row:hover { padding-left: 13px; }
  .leader-arrow { display: none; }
  .leader-count { font-size: 12px; }
  .gratitude-note { align-items: flex-start; flex-wrap: wrap; gap: 10px 13px; margin-top: 58px; }
  .gratitude-note p { flex-basis: calc(100% - 52px); }
  .gratitude-note a { margin-left: 51px; }
}

@media (max-width: 360px) {
  .community-hero { padding-inline: 22px; }
  .community-stats { display: grid; grid-template-columns: repeat(2, 1fr); }
  .wall-grid { grid-template-columns: 1fr; }
  .wall-card { min-height: 174px; }
}
</style>
