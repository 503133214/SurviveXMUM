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

initTheme();

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus);
app.directive("reveal", reveal);
axios.defaults.baseURL = 'http://localhost:8080';

router.isReady().then(() => {
  if (!BACKEND_ENABLED) return;
  const userStore = useUserStore();
  userStore.fetchUserInfo();
});

app.mount("#app");
