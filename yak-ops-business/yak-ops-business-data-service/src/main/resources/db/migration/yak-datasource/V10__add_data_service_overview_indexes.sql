-- Support bounded Data Service overview aggregation and recent-failure reads.
ALTER TABLE yak_ops_data_service_call_log
    ADD KEY idx_yak_ops_data_service_log_time_api (create_time, api_id),
    ADD KEY idx_yak_ops_data_service_log_success_time_id (success, create_time, id);
