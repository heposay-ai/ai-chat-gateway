-- Workflow inputs and outputs contain natural-language text, not JSON documents.
-- Run this migration against existing MySQL databases before deploying the fix.

ALTER TABLE workflow_execution
    MODIFY COLUMN input LONGTEXT NULL COMMENT '输入参数',
    MODIFY COLUMN output LONGTEXT NULL COMMENT '输出结果';

ALTER TABLE node_execution
    MODIFY COLUMN input LONGTEXT NULL COMMENT '输入',
    MODIFY COLUMN output LONGTEXT NULL COMMENT '输出';
