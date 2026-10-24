-- Yak Ops v1.5.0: summary (DWS) and application (ADS) logical models.
--
-- The model kind itself needs no schema change: `type` is a VARCHAR without a value CHECK, so the
-- widened enum reaches the database as plain text. What is genuinely new is the measure attribute on
-- fields, plus ownership guards for the two kinds so a hand-written row cannot contradict them.

ALTER TABLE yak_ops_modeling_model_field
    ADD COLUMN aggregate_type VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT 'Optional measure function: SUM, COUNT, AVG, MAX or MIN; null for a plain column'
        AFTER is_partition,
    ADD CONSTRAINT ck_modeling_model_field_aggregate CHECK (
        aggregate_type IS NULL OR aggregate_type IN ('SUM', 'COUNT', 'AVG', 'MAX', 'MIN'));

ALTER TABLE yak_ops_modeling_model_field
    COMMENT = 'Ordered logical fields of a DWD, DIM, DWS or ADS model';

ALTER TABLE yak_ops_modeling_model
    ADD CONSTRAINT ck_modeling_model_dws CHECK (
        type <> 'DWS'
        OR (process_id IS NOT NULL AND domain_id IS NOT NULL AND datamart_id IS NULL AND dimension_id IS NULL)),
    ADD CONSTRAINT ck_modeling_model_ads CHECK (
        type <> 'ADS'
        OR (datamart_id IS NOT NULL AND process_id IS NULL AND dimension_id IS NULL));

ALTER TABLE yak_ops_modeling_model
    COMMENT = 'Workspace-owned DWD, DIM, DWS or ADS logical model';
