import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { createPinia } from "pinia";
import ElementPlus from 'element-plus'
import "./assets/global.css";
import 'element-plus/dist/index.css'
import axios from "axios";
import { useUserStore } from "./store/userStore.js";

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus);
axios.defaults.baseURL = 'http://localhost:8080';

router.isReady().then(() => {
  const userStore = useUserStore();
  userStore.fetchUserInfo();
});

app.mount("#app");
