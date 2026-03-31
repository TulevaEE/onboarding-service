package ee.tuleva.onboarding.aml.risklevel;

public final class TkfRiskTestDataFixtures {

  public static final String TKF_PERSON_HIGH_RISK = "38501010001";
  public static final String TKF_PERSON_MEDIUM_RISK = "39001010002";
  public static final String TKF_PERSON_LOW_RISK = "49001010003";

  public static final String CREATE_TKF_RISK_TABLE =
      "CREATE TABLE IF NOT EXISTS analytics.v_tkf_risk_metadata ("
          + "personal_id VARCHAR(50) PRIMARY KEY,"
          + "risk_level INT,"
          + "metadata JSON"
          + ")";

  public static final String TRUNCATE_TKF_RISK = "TRUNCATE TABLE analytics.v_tkf_risk_metadata";

  public static final String INSERT_TKF_HIGH_RISK =
      "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES ("
          + "'"
          + TKF_PERSON_HIGH_RISK
          + "', 1, "
          + "'{\"version\": \"1.2\", \"level\": 1, \"total_points\": 105, "
          + "\"rule_1_non_citizen_non_resident\": 100, \"rule_2_high_risk_citizen\": 0, "
          + "\"rule_3_citizen_in_high_risk\": 0, \"rule_4_sanctioned\": 0, "
          + "\"rule_5_pep_non_eea\": 0, \"rule_6_pep_eea\": 5, "
          + "\"rule_7_resident_foreigner\": 0, \"rule_12_existing_high_vol\": 0, "
          + "\"rule_13_new_high_vol\": 0, \"rule_14_non_eea_tx\": 0, "
          + "\"rule_15_high_risk_tx\": 0, \"rule_18_salary_mismatch\": 0, "
          + "\"rule_20_no_3rd_pillar\": 0, \"rule_21_third_party\": 0, "
          + "\"rule_22_third_party_high_risk\": 0, \"rule_23_underage_no_3rd\": 0}'"
          + ")";

  public static final String INSERT_TKF_MEDIUM_RISK =
      "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES ("
          + "'"
          + TKF_PERSON_MEDIUM_RISK
          + "', 2, "
          + "'{\"version\": \"1.2\", \"level\": 2, \"total_points\": 20, "
          + "\"rule_1_non_citizen_non_resident\": 0, \"rule_2_high_risk_citizen\": 0, "
          + "\"rule_3_citizen_in_high_risk\": 0, \"rule_4_sanctioned\": 0, "
          + "\"rule_5_pep_non_eea\": 0, \"rule_6_pep_eea\": 5, "
          + "\"rule_7_resident_foreigner\": 5, \"rule_12_existing_high_vol\": 0, "
          + "\"rule_13_new_high_vol\": 0, \"rule_14_non_eea_tx\": 10, "
          + "\"rule_15_high_risk_tx\": 0, \"rule_18_salary_mismatch\": 0, "
          + "\"rule_20_no_3rd_pillar\": 0, \"rule_21_third_party\": 0, "
          + "\"rule_22_third_party_high_risk\": 0, \"rule_23_underage_no_3rd\": 0}'"
          + ")";

  public static final String INSERT_TKF_LOW_RISK =
      "INSERT INTO analytics.v_tkf_risk_metadata (personal_id, risk_level, metadata) VALUES ("
          + "'"
          + TKF_PERSON_LOW_RISK
          + "', 3, "
          + "'{\"version\": \"1.2\", \"level\": 3, \"total_points\": 5, "
          + "\"rule_1_non_citizen_non_resident\": 0, \"rule_2_high_risk_citizen\": 0, "
          + "\"rule_3_citizen_in_high_risk\": 0, \"rule_4_sanctioned\": 0, "
          + "\"rule_5_pep_non_eea\": 0, \"rule_6_pep_eea\": 0, "
          + "\"rule_7_resident_foreigner\": 0, \"rule_12_existing_high_vol\": 0, "
          + "\"rule_13_new_high_vol\": 0, \"rule_14_non_eea_tx\": 0, "
          + "\"rule_15_high_risk_tx\": 0, \"rule_18_salary_mismatch\": 0, "
          + "\"rule_20_no_3rd_pillar\": 5, \"rule_21_third_party\": 0, "
          + "\"rule_22_third_party_high_risk\": 0, \"rule_23_underage_no_3rd\": 0}'"
          + ")";
}
