import {createRouter, createWebHistory} from "vue-router";

const routes = [
  {
    path: "/",
    name: "Home",
    component: () => import(/* webpackChunkName: "home" */ "@/views/HomePage.vue"),
  },
  {
    path: "/login",
    name: "Login",
    component: () => import(/* webpackChunkName: "login" */ "@/views/LoginPage.vue"),
  },
  {
    path: "/docs/:pathMatch(.*)*",
    name: "DocPage",
    component: () => import(/* webpackChunkName: "docpage" */ "@/views/DocPage.vue"),
    props: route => {
      const pathMatch = route.params.pathMatch;
      // 如果 pathMatch 是数组（多段路径），将其合并为字符串
      const pathString = Array.isArray(pathMatch) ? pathMatch.join('/') : pathMatch;
      return { pathMatch: pathString || '' };
    },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import(/* webpackChunkName: "notfound" */ "@/views/NotFound.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

export default router;
