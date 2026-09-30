<template>
  <a class="skip-link" href="#main">跳到正文</a>
  <Header />
  <main id="main" class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </main>
  <CommandPalette />
</template>

<script>
import Header from '@/components/Header.vue';
import CommandPalette from '@/components/CommandPalette.vue';

export default {
  name: 'App',
  components: {
    Header,
    CommandPalette,
  },
};
</script>

<style>
#app {
  font-family: var(--font-sans);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: var(--text-body);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 与 #app 同样是纵向 flex：路由页面仍是 flex 子项，min-height/flex:1 的写法照旧生效 */
.app-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}
.app-main > * {
  width: 100%;
  min-width: 0;
}

/* 键盘用户按 Tab 时第一个出现的「跳到正文」 */
.skip-link {
  position: fixed;
  top: 10px;
  left: 10px;
  z-index: 4000;
  padding: 8px 14px;
  border-radius: 10px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 14px;
  font-weight: 600;
  transform: translateY(-160%);
  transition: transform 0.2s var(--ease-out);
}
.skip-link:focus { transform: none; color: var(--accent-contrast); text-decoration: none; }
</style>
