-- 数据服务：支持由数据开发 Data Service Node 控制返回结果分页。
-- 历史服务保持关闭，避免升级后改变既有 Runtime 响应语义。
ALTER TABLE yak_ops_data_service_api
    ADD COLUMN pagination_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用返回结果分页' AFTER timeout_seconds;
