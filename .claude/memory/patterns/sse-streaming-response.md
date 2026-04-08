---
name: SSE 流式响应
category: controller
reuse_count: 2
---

## 适用场景
- AI 对话流式输出
- 长时间任务进度推送
- 实时日志输出

## 代码模板

### Controller
```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamChat(@RequestParam String message) {
    return chatService.streamChat(message)
        .map(content -> ServerSentEvent.<String>builder()
            .event("message")
            .data(content)
            .build())
        .concatWith(Flux.just(ServerSentEvent.<String>builder()
            .event("done")
            .data("[DONE]")
            .build()));
}
```

### Service
```java
public Flux<String> streamChat(String message) {
    return chatClient.prompt()
        .user(message)
        .stream()
        .content();
}
```

### 前端消费
```javascript
const eventSource = new EventSource(`/api/chat/stream?message=${encodeURIComponent(msg)}`);
eventSource.addEventListener('message', (e) => {
    if (e.data !== '[DONE]') {
        appendContent(e.data);
    }
});
eventSource.addEventListener('done', () => {
    eventSource.close();
});
```

## 注意事项
- 设置合适的超时时间
- 前端需处理连接断开重连
- 考虑添加心跳机制防止连接超时
