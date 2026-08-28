-- Support detail-page reads: WHERE api_id = ? ORDER BY create_time DESC, id DESC LIMIT ?.
ALTER TABLE yak_ops_data_service_call_log
    ADD KEY idx_yak_ops_data_service_log_api_time_id (api_id, create_time, id);
