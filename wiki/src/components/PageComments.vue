<template>
  <section class="page-comments" aria-labelledby="page-comments-title">
    <div class="pc-heading">
      <div>
        <p class="pc-eyebrow">读者讨论</p>
        <h2 id="page-comments-title">
          讨论区
          <span v-if="total" class="pc-total">{{ total }} 条</span>
        </h2>
        <p class="pc-note">补充信息、提问、纠错都欢迎；请友善发言</p>
      </div>
    </div>

    <!-- 发表主楼 -->
    <form v-if="userStore.isLoggedIn" class="pc-composer" @submit.prevent="submitRoot">
      <el-input
        v-model="rootDraft"
        type="textarea"
        :rows="3"
        :maxlength="MAX_LEN"
        show-word-limit
        resize="none"
        placeholder="说点什么…（请勿发布个人隐私信息）"
      />
      <div class="pc-composer-actions">
        <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!rootDraft.trim()">
          发表
        </el-button>
      </div>
    </form>
    <p v-else class="pc-guest">
      <router-link :to="loginLink">登录</router-link>
      后即可参与讨论。
    </p>

    <el-skeleton v-if="loading" :rows="3" animated />
    <p v-else-if="error" class="pc-status">{{ error }}</p>
    <p v-else-if="!threads.length" class="pc-status">还没有人讨论这一页，来做第一个吧。</p>

    <ul v-else class="pc-list">
      <li v-for="thread in threads" :key="thread.id" class="pc-thread">
        <article class="pc-item" :class="{ removed: thread.status !== 'VISIBLE' }">
          <el-avatar :size="34" :src="thread.avatar || undefined">
            {{ initial(thread.displayName) }}
          </el-avatar>
          <div class="pc-body">
            <div class="pc-meta">
              <component
                :is="thread.userId ? 'router-link' : 'span'"
                class="pc-name"
                :to="thread.userId ? `/contributors/${thread.userId}` : undefined"
              >{{ thread.displayName || '——' }}</component>
              <span class="pc-time">{{ thread.createdAt }}</span>
            </div>
            <p class="pc-text">{{ thread.content }}</p>
            <div v-if="thread.status === 'VISIBLE'" class="pc-actions">
              <button v-if="userStore.isLoggedIn" type="button" @click="openReply(thread.id, thread.displayName)">
                回复
              </button>
              <button v-if="thread.mine" type="button" class="danger" @click="removeMine(thread)">删除</button>
              <button v-if="userStore.isAdmin" type="button" class="danger" @click="hide(thread)">隐藏</button>
            </div>
          </div>
        </article>

        <!-- 楼中回复 -->
        <ul v-if="thread.replies && thread.replies.length" class="pc-replies">
          <li v-for="reply in thread.replies" :key="reply.id">
            <article class="pc-item pc-item-reply">
              <el-avatar :size="28" :src="reply.avatar || undefined">
                {{ initial(reply.displayName) }}
              </el-avatar>
              <div class="pc-body">
                <div class="pc-meta">
                  <component
                    :is="reply.userId ? 'router-link' : 'span'"
                    class="pc-name"
                    :to="reply.userId ? `/contributors/${reply.userId}` : undefined"
                  >{{ reply.displayName || '——' }}</component>
                  <span v-if="reply.replyToName" class="pc-replyto">回复 @{{ reply.replyToName }}</span>
                  <span class="pc-time">{{ reply.createdAt }}</span>
                </div>
                <p class="pc-text">{{ reply.content }}</p>
                <div class="pc-actions">
                  <button v-if="userStore.isLoggedIn" type="button" @click="openReply(reply.id, reply.displayName)">
                    回复
                  </button>
                  <button v-if="reply.mine" type="button" class="danger" @click="removeMine(reply)">删除</button>
                  <button v-if="userStore.isAdmin" type="button" class="danger" @click="hide(reply)">隐藏</button>
                </div>
              </div>
            </article>
          </li>
        </ul>

        <!-- 回复输入框：跟随被回复的那一条挂在本楼底部 -->
        <form v-if="replyTo && replyTo.rootId === thread.id" class="pc-reply-form" @submit.prevent="submitReply">
          <el-input
            v-model="replyDraft"
            type="textarea"
            :rows="2"
            :maxlength="MAX_LEN"
            show-word-limit
            resize="none"
            :placeholder="`回复 @${replyTo.name}`"
          />
          <div class="pc-composer-actions">
            <el-button text @click="replyTo = null">取消</el-button>
            <el-button type="primary" native-type="submit" :loading="submitting" :disabled="!replyDraft.trim()">
              回复
            </el-button>
          </div>
        </form>
      </li>
    </ul>
  </section>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listComments,
  postComment,
  deleteComment,
  adminSetCommentStatus,
} from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

const MAX_LEN = 1000

export default {
  name: 'PageComments',
  props: {
    docPath: { type: String, required: true },
  },
  data() {
    return {
      MAX_LEN,
      userStore: useUserStore(),
      threads: [],
      loading: false,
      error: '',
      submitting: false,
      rootDraft: '',
      replyDraft: '',
      // { id: 被回复的评论 id, rootId: 所在主楼 id, name: 被回复者显示名 }
      replyTo: null,
      requestToken: 0,
    }
  },
  computed: {
    total() {
      return this.threads.reduce(
        (n, t) => n + (t.status === 'VISIBLE' ? 1 : 0) + (t.replies ? t.replies.length : 0),
        0,
      )
    },
    loginLink() {
      return { path: '/login', query: { redirect: this.$route.fullPath } }
    },
  },
  watch: {
    docPath: {
      immediate: true,
      handler() {
        this.resetDrafts()
        this.load()
      },
    },
  },
  methods: {
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase()
    },
    resetDrafts() {
      this.rootDraft = ''
      this.replyDraft = ''
      this.replyTo = null
    },
    load() {
      if (!this.docPath) return
      const token = ++this.requestToken
      this.loading = true
      this.error = ''
      listComments(
        this.docPath,
        (data) => {
          if (token !== this.requestToken) return
          this.threads = data || []
          this.loading = false
        },
        (message) => {
          if (token !== this.requestToken) return
          this.error = message || '讨论加载失败'
          this.loading = false
        },
      )
    },
    openReply(id, name) {
      const root = this.threads.find(
        (t) => t.id === id || (t.replies || []).some((r) => r.id === id),
      )
      if (!root) return
      this.replyTo = { id, rootId: root.id, name: name || '' }
      this.replyDraft = ''
    },
    submitRoot() {
      this.send({ path: this.docPath, content: this.rootDraft }, () => {
        this.rootDraft = ''
      })
    },
    submitReply() {
      if (!this.replyTo) return
      this.send(
        { path: this.docPath, content: this.replyDraft, parentId: this.replyTo.id },
        () => {
          this.replyDraft = ''
          this.replyTo = null
        },
      )
    },
    send(payload, onDone) {
      if (this.submitting) return
      this.submitting = true
      postComment(
        payload,
        () => {
          this.submitting = false
          onDone()
          ElMessage.success('已发表')
          this.load()
        },
        (message) => {
          this.submitting = false
          ElMessage.error(message || '发表失败')
        },
      )
    },
    async removeMine(comment) {
      try {
        await ElMessageBox.confirm('确定删除这条评论？', '提示', { type: 'warning' })
      } catch {
        return
      }
      deleteComment(
        comment.id,
        () => {
          ElMessage.success('已删除')
          this.load()
        },
        (message) => ElMessage.error(message || '删除失败'),
      )
    },
    async hide(comment) {
      let reason = ''
      try {
        const { value } = await ElMessageBox.prompt('隐藏理由（会通知作者，可留空）', '隐藏这条评论', {
          type: 'warning',
          inputPlaceholder: '如：与本页主题无关',
          inputValidator: (v) => (v || '').length <= 200 || '理由最多 200 字',
        })
        reason = value || ''
      } catch {
        return
      }
      adminSetCommentStatus(
        comment.id,
        { status: 'HIDDEN', reason },
        () => {
          ElMessage.success('已隐藏')
          this.load()
        },
        (message) => ElMessage.error(message || '操作失败'),
      )
    },
  },
}
</script>

<style scoped>
.page-comments {
  max-width: 1360px;
  margin: 20px auto 0;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}

.pc-heading { margin-bottom: 16px; }

.pc-eyebrow {
  margin: 0 0 4px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}

.pc-heading h2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  color: var(--text-primary);
  font-size: 1.08rem;
  line-height: 1.3;
}

.pc-total { color: var(--text-muted); font-size: 12px; font-weight: 500; }
.pc-note { margin: 5px 0 0; color: var(--text-muted); font-size: 12px; }

.pc-composer { margin-bottom: 18px; }
.pc-composer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }

.pc-guest {
  margin: 0 0 18px;
  padding: 12px 14px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: 13px;
}
.pc-guest a { color: var(--brand); font-weight: 600; }

.pc-status { margin: 0; color: var(--text-muted); font-size: 13px; }

.pc-list, .pc-replies { margin: 0; padding: 0; list-style: none; }

.pc-thread + .pc-thread {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

.pc-item { display: flex; gap: 11px; }
.pc-item.removed { opacity: .6; }
.pc-body { min-width: 0; flex: 1; }

.pc-meta {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 4px;
}

.pc-name {
  color: var(--text-primary);
  font-size: 13.5px;
  font-weight: 600;
  text-decoration: none;
}
a.pc-name:hover { color: var(--brand); }

.pc-replyto { color: var(--text-muted); font-size: 12px; }
.pc-time { color: var(--text-muted); font-size: 11.5px; }

.pc-text {
  margin: 0;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.72;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.pc-actions { display: flex; gap: 12px; margin-top: 6px; }

.pc-actions button {
  padding: 0;
  border: none;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.pc-actions button:hover { color: var(--brand); }
.pc-actions button.danger:hover { color: var(--el-color-danger, #f56c6c); }

.pc-replies {
  margin: 12px 0 0 45px;
  padding-left: 14px;
  border-left: 2px solid var(--border);
}
.pc-replies > li + li { margin-top: 12px; }

.pc-reply-form { margin: 12px 0 0 45px; }

@media (max-width: 600px) {
  .page-comments { padding: 16px; }
  .pc-note { display: none; }
  .pc-replies, .pc-reply-form { margin-left: 12px; }
}
</style>
