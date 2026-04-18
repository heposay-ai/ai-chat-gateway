package com.example.aichat.workflow.engine;

/**
 * 节点执行结果
 */
public class NodeResult {

    private String output;
    private String branch;   // 条件分支标识: "true"/"false"
    private boolean paused;  // 是否暂停（人工审批）

    private NodeResult() {}

    public static NodeResult of(String output) {
        NodeResult r = new NodeResult();
        r.output = output;
        return r;
    }

    public static NodeResult branch(String branchLabel) {
        NodeResult r = new NodeResult();
        r.branch = branchLabel;
        return r;
    }

    public static NodeResult paused() {
        NodeResult r = new NodeResult();
        r.paused = true;
        return r;
    }

    public String getOutput() { return output; }
    public String getBranch() { return branch; }
    public boolean isPaused() { return paused; }
}
