-- Rename the saved MySQL Connector/J timezone key once.
-- Both JSON columns are edited independently: normalized execution inputs and editor state.
-- Prefer an already configured canonical value when both keys exist.
UPDATE yak_ops_data_source
SET connection_params = JSON_REMOVE(
        JSON_SET(
            connection_params,
            '$.properties.connectionTimeZone',
            COALESCE(
                JSON_EXTRACT(connection_params, '$.properties.connectionTimeZone'),
                JSON_EXTRACT(connection_params, '$.properties.serverTimezone'))),
        '$.properties.serverTimezone')
WHERE db_type = 'MYSQL'
  AND JSON_VALID(connection_params)
  AND JSON_CONTAINS_PATH(connection_params, 'one', '$.properties.serverTimezone');

UPDATE yak_ops_data_source
SET original_json = JSON_REMOVE(
        JSON_SET(
            original_json,
            '$.properties.connectionTimeZone',
            COALESCE(
                JSON_EXTRACT(original_json, '$.properties.connectionTimeZone'),
                JSON_EXTRACT(original_json, '$.properties.serverTimezone'))),
        '$.properties.serverTimezone')
WHERE db_type = 'MYSQL'
  AND JSON_VALID(original_json)
  AND JSON_CONTAINS_PATH(original_json, 'one', '$.properties.serverTimezone');
