import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { createPinia } from "pinia";
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import "./assets/global.css";
import axios from "axios";
import { useUserStore } from "./store/userStore.js";
import { initTheme } from "./composables/useTheme.js";
import { reveal } from "./directives/reveal.js";
import { BACKEND_ENABLED } from "./config.js";
import { loadManifest } from "./wiki/index.js";

initTheme();

// 所有 API 走 /api 前缀：dev 由 vite proxy 转发，prod 由 nginx 反向代理到 Spring Boot。
axios.defaults.baseURL = "/api";
// 弱网下快速失败，避免请求无限挂起导致界面一直空白。
axios.defaults.timeout = 20000;

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus);
app.directive("reveal", reveal);

// 拉取内容清单（侧栏/首页/搜索）。不阻塞挂载，数据就绪后响应式填充。
loadManifest();

router.isReady().then(() => {
  if (!BACKEND_ENABLED) return;
  const userStore = useUserStore();
  userStore.fetchUserInfo();
});

app.mount("#app");

// 应用成功启动：清除「资源加载失败强制刷新」标记，使日后新部署仍能再次自愈（见 index.html）。
try { sessionStorage.removeItem("asset-reload"); } catch (e) { /* ignore */ }
