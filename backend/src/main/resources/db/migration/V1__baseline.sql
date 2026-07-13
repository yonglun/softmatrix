-- V1__baseline.sql — 切片一 agent 表基线(全新库上创建;已有库上被 Flyway 基线跳过)
CREATE TABLE IF NOT EXISTS agent (
    id                  uuid PRIMARY KEY,
    name                varchar(100) NOT NULL,
    description         text,
    flowise_chatflow_id varchar(64) NOT NULL,
    owner               varchar(100) NOT NULL,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL
);
