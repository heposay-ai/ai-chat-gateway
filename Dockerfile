# syntax=docker/dockerfile:1.7

# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 配置阿里云 Maven 镜像
RUN mkdir -p ~/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><name>Aliyun Maven</name><url>https://maven.aliyun.com/repository/public</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>' > ~/.m2/settings.xml

# 先拷贝 pom.xml，单独下载依赖（pom 不变时这层被缓存）
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn dependency:go-offline -B

# 再拷贝源码编译
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn clean package -DskipTests -B

# 第二阶段：运行
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/target/ai-chat-gateway-1.0.0.jar app.jar

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", \
    "--spring.profiles.active=prod"]
