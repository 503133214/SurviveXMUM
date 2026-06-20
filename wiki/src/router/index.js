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

router.beforeEach((to, from, next) => {
  const { loggedIn, role } = readAuth();
  if (to.meta.requiresAuth && !loggedIn) {
    return next({ path: "/login", query: { redirect: to.fullPath } });
  }
  if (to.meta.requiresAdmin && role !== "ADMIN") {
    return next("/");
  }
  next();
});

export default router;
