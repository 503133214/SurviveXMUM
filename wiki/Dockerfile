# syntax=docker/dockerfile:1

# ---- 构建阶段：Node 编译出静态 dist ----
FROM node:20-alpine AS build
WORKDIR /app

# 先装依赖，利用层缓存
COPY package*.json ./
RUN npm ci

# 再拷源码构建（内容在运行时从 /api 取，构建只打包前端代码）
COPY . .
RUN npm run build

# ---- 运行阶段：nginx 托管静态文件 ----
FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
