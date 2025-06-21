import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import { createPinia } from "pinia";
import ElementPlus from 'element-plus'
import "./assets/global.css"; // 后面会新建全局样式文件
import 'element-plus/dist/index.css'
import axios from "axios"; // 引入 Element Plus 样式

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(ElementPlus) // 注册 Element Plus 所有组件
axios.defaults.baseURL = 'http://localhost:8080'
app.mount("#app");
