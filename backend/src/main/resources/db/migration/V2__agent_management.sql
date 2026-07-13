-- V2__agent_management.sql — Agent 管理加深:分类/标签/状态/发布时间
ALTER TABLE agent ADD COLUMN category     varchar(50);
ALTER TABLE agent ADD COLUMN tags         text[] NOT NULL DEFAULT '{}';
ALTER TABLE agent ADD COLUMN status       varchar(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE agent ADD COLUMN published_at timestamptz;

-- 存量 Agent(切片一已能运行)一次性置为 PUBLISHED,避免破坏演示
UPDATE agent SET status = 'PUBLISHED', published_at = now();
