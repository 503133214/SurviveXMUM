import { createRouter, createWebHistory } from "vue-router";

const routes = [
  {
    path: "/",
    name: "Home",
    component: () => import("@/views/HomePage.vue"),
  },
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/LoginPage.vue"),
  },
  {
    path: "/profile",
    name: "Profile",
    component: () => import("@/views/ProfilePage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/favorites",
    name: "Favorites",
    component: () => import("@/views/FavoritesPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/feedback",
    name: "Feedback",
    component: () => import("@/views/FeedbackPage.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/edit/:pathMatch(.*)*",
    name: "Edit",
    component: () => import("@/views/EditPage.vue"),
    meta: { requiresAuth: true },
    props: (route) => {
      const pm = route.params.pathMatch;
      const pathString = Array.isArray(pm) ? pm.join("/") : pm;
      return { targetPath: pathString || "" };
    },
  },
  {
    path: "/admin",
    name: "Admin",
    component: () => import("@/views/AdminPage.vue"),
    meta: { requiresAuth: true, requiresAdmin: true },
  },
  {
    path: "/docs/:pathMatch(.*)*",
    name: "DocPage",
    component: () => import("@/views/DocPage.vue"),
    props: (route) => {
      const pathMatch = route.params.pathMatch;
      const pathString = Array.isArray(pathMatch) ? pathMatch.join("/") : pathMatch;
      return { pathMatch: pathString || "" };
    },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/views/NotFound.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// 从 localStorage 的 JWT 解析登录态与角色（无需等待 fetchUserInfo）。
function readAuth() {
  const raw = localStorage.getItem("token");
  if (!raw) return { loggedIn: false, role: null };
  try {
    const authObj = JSON.parse(raw);
    if (!(authObj.expire > Date.now())) return { loggedIn: false, role: null };
    const payload = JSON.parse(atob(authObj.token.split(".")[1]));
    return { loggedIn: true, role: payload.role || null };
  } catch {
    return { loggedIn: false, role: null };
  }
}

// 记录正在前往的目标，供 onError 在动态加载失败时整页跳转过去。
let pendingFullPath = null;
router.beforeEach((to, from, next) => {
  pendingFullPath = to.fullPath;
  const { loggedIn, role } = readAuth();
  if (to.meta.requiresAuth && !loggedIn) {
    return next({ path: "/login", query: { redirect: to.fullPath } });
  }
  if (to.meta.requiresAdmin && role !== "ADMIN" && role !== "SUPER_ADMIN") {
    return next("/");
  }
  next();
});

// 导航成功后清掉该路径的“已重试”标记，使后续真正的失败仍能再次自愈。
router.afterEach((to) => {
  sessionStorage.removeItem("chunk-reload:" + to.fullPath);
});

// 部署后，旧标签页里内存中的旧 index 会去 import 已被替换的旧 chunk（文件名带 hash），
// 导致“动态模块加载失败” → 路由内容空白（如管理后台白屏）。此时强制整页加载到目标地址，
// 浏览器会重新拿到最新的 index.html 与 chunk。用 sessionStorage 防止失败时无限刷新。
router.onError((error) => {
  const msg = (error && error.message) || "";
  if (/dynamically imported module|module script failed|Failed to fetch/i.test(msg)) {
    const target = pendingFullPath || window.location.pathname;
    const key = "chunk-reload:" + target;
    if (!sessionStorage.getItem(key)) {
      sessionStorage.setItem(key, "1");
      window.location.assign(target);
    }
  }
});

export default router;
