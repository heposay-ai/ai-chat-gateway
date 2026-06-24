package com.example.aichat.proxy.gateway;

import com.example.aichat.proxy.model.entity.ProxyAccount;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 加权随机策略 (默认)
 */
@Component
public class RandomWeightedStrategy implements ProxyRoutingStrategy {

    @Override
    public ProxyAccount select(List<ProxyAccount> candidates, String model) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // 如果指定了模型, 优先过滤支持该模型的账号
        if (model != null && !model.isBlank()) {
            List<ProxyAccount> modelMatched = candidates.stream()
                    .filter(a -> supportsModel(a, model))
                    .toList();
            if (!modelMatched.isEmpty()) {
                candidates = modelMatched;
            }
            // 如果没有匹配的, 退回到全量候选
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // 加权随机
        int totalWeight = candidates.stream()
                .mapToInt(a -> a.getWeight() != null ? a.getWeight() : 1)
                .sum();

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;

        for (ProxyAccount account : candidates) {
            cumulative += (account.getWeight() != null ? account.getWeight() : 1);
            if (random < cumulative) {
                return account;
            }
        }

        return candidates.get(0);
    }

    private boolean supportsModel(ProxyAccount account, String model) {
        if (account.getSupportedModels() == null || account.getSupportedModels().isBlank()) {
            return true; // 未配置模型列表视为支持所有
        }
        return account.getSupportedModels().toLowerCase().contains(model.toLowerCase());
    }
}
