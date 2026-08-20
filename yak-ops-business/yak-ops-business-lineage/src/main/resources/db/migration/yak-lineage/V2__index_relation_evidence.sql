-- Stage 2 SQL lineage replaces generated relations by parser evidence on every publish.
ALTER TABLE yak_metadata_relation
    ADD KEY idx_yak_metadata_relation_evidence (source_type, source_id);
