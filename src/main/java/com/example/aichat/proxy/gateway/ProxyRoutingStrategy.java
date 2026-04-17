package com.example.aichat.proxy.gateway;

import com.example.aichat.proxy.model.entity.ProxyAccount;

import java.util.List;

/**
 * 代理路由策略接口
 * 预留扩展: 负载均衡、智能编排、故障转移
 */
public interface ProxyRoutingStrategy {

    /**
     * 从候选账号中选择一个用于转发
     *
     * @param candidates 健康且启用的候选账号
     * @param model      请求的模型名称
     * @return 选中的账号, null 表示无可用账号
     */
    ProxyAccount select(List<ProxyAccount> candidates, String model);
}
