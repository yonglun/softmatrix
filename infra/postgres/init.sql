-- infra/postgres/init.sql
-- 单实例、三个独立数据库。Keycloak 与 Flowise 需要各自的库。
CREATE DATABASE portal;
CREATE DATABASE keycloak;
CREATE DATABASE flowise;
