# syntax=docker/dockerfile:1

# ---- 构建阶段：用 Maven 编译出 jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom 下载依赖，利用 Docker 层缓存：源码改动时不必重新下依赖
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# 再拷源码编译。跳过测试：测试依赖 MySQL/Redis，构建机上没有，留到本地或 CI 跑
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- 运行阶段：仅 JRE，镜像更小 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# pom 里 finalName=wiki-backend → 产物为 target/wiki-backend.jar
COPY --from=build /build/target/wiki-backend.jar app.jar

EXPOSE 8080
# JAVA_OPTS 可在 compose 里覆盖（如限制堆内存）
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
