# syntax=docker/dockerfile:1.7

# 第一阶段：构建前端
FROM node:18-alpine AS builder

WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# 第二阶段：Nginx 运行
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
