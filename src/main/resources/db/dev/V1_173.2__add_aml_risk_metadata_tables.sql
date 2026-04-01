CREATE TABLE IF NOT EXISTS analytics.v_aml_risk (
    personal_id VARCHAR(50),
    attribute_1 INT,
    attribute_2 INT,
    attribute_3 INT,
    attribute_4 INT,
    attribute_5 INT,
    risk_level INT,
    version TEXT DEFAULT '2.0'
);

CREATE OR REPLACE VIEW analytics.v_aml_risk_metadata AS
SELECT personal_id,
       risk_level,
       json_build_object(
         'version', version,
         'level', risk_level,
         'attribute_1', attribute_1,
         'attribute_2', attribute_2,
         'attribute_3', attribute_3,
         'attribute_4', attribute_4,
         'attribute_5', attribute_5
       ) AS metadata
FROM analytics.v_aml_risk;
