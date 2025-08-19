package ee.tuleva.onboarding.aml.risklevel;

public final class AmlRiskTestDataFixtures {

  public static final String PERSON_ID_1 = "111";
  public static final String PERSON_ID_2 = "222";
  public static final String PERSON_ID_3 = "1234567890";
  public static final String PERSON_ID_4 = "37605030299";
  public static final String PERSON_ID_5 = "39107050268";
  public static final String PERSON_ID_6 = "38812022762";
  public static final String PERSON_ID_7 = "9876543210";
  public static final String PERSON_ID_BLANK = "        ";

  public static final String CREATE_AML_RISK_SHCEMA = "CREATE SCHEMA IF NOT EXISTS analytics";
  public static final String CREATE_AML_RISK_VIEW =
      "CREATE TABLE IF NOT EXISTS analytics.v_aml_risk ("
          + "personal_id VARCHAR(50),"
          + "attribute_1 INT,"
          + "attribute_2 INT,"
          + "attribute_3 INT,"
          + "attribute_4 INT,"
          + "attribute_5 INT,"
          + "risk_level INT,"
          + "version TEXT DEFAULT '2.0'"
          + ")";
  public static final String TRUNCATE_AML_RISK = "TRUNCATE TABLE analytics.v_aml_risk";

  public static final String CREATE_AML_RISK_METADATA_VIEW =
      "CREATE OR REPLACE VIEW analytics.v_aml_risk_metadata AS "
          + "SELECT personal_id,"
          + "       risk_level,"
          + "       JSON_OBJECT("
          + "         KEY 'version' VALUE version,"
          + "         KEY 'level' VALUE risk_level,"
          + "         KEY 'attribute_1' VALUE attribute_1,"
          + "         KEY 'attribute_2' VALUE attribute_2,"
          + "         KEY 'attribute_3' VALUE attribute_3,"
          + "         KEY 'attribute_4' VALUE attribute_4,"
          + "         KEY 'attribute_5' VALUE attribute_5"
          + "       ) AS metadata "
          + "FROM analytics.v_aml_risk";

  public static final String INSERT_PERSON_1_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_1
          + "', 1, 2, 3, 4, 5, 1"
          + ")";

  public static final String INSERT_PERSON_2_RISK_LEVEL_2 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_2
          + "', 1, 2, 3, 4, 5, 2"
          + ")";

  public static final String INSERT_PERSON_3_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_3
          + "', 1, 1, 1, 1, 1, 1"
          + ")";

  public static final String INSERT_PERSON_4_2_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_4
          + "', 3, 2, 0, 0, 0, 1"
          + ")";

  public static final String INSERT_PERSON_BLANK_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_BLANK
          + "', 5, 1, 0, 0, 0, 1"
          + ")";

  public static final String INSERT_PERSON_5_RISK_LEVEL_2 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_5
          + "', 0, 0, 0, 0, 0, 2"
          + ")";

  public static final String INSERT_PERSON_6_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_6
          + "', 5, 4, 0, 0, 0, 1"
          + ")";

  public static final String INSERT_PERSON_4_RISK_LEVEL_1 =
      "INSERT INTO analytics.v_aml_risk ("
          + "personal_id, attribute_1, attribute_2, attribute_3, attribute_4, attribute_5, risk_level"
          + ") VALUES ("
          + "'"
          + PERSON_ID_4
          + "', 3, 2, 0, 0, 0, 1"
          + ")";
}
