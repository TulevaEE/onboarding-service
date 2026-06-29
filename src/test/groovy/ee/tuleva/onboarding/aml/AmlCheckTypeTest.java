package ee.tuleva.onboarding.aml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmlCheckTypeTest {

  @Test
  void hasKybCheckTypes() {
    assertThat(AmlCheckType.valueOf("KYB_SOLE_MEMBER_OWNERSHIP")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_DUAL_MEMBER_OWNERSHIP")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_SINGLE_BOARD_MEMBER_OWNERSHIP")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_COMPANY_ACTIVE")).isNotNull();
  }

  @Test
  void hasManualEventType() {
    assertThat(AmlCheckType.valueOf("MANUAL_EVENT")).isNotNull();
  }

  @Test
  void manualEventIsNotClientManual() {
    // MANUAL_EVENT is specialist-created (Metabase action), not a client-self-addable check
    assertThat(AmlCheckType.MANUAL_EVENT.isManual()).isFalse();
  }

  @Test
  void kybCheckTypesAreNotManual() {
    assertThat(AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_SINGLE_BOARD_MEMBER_OWNERSHIP.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_COMPANY_ACTIVE.isManual()).isFalse();
  }

  @Test
  void hasCompanyRiskLevelOverrideType() {
    assertThat(AmlCheckType.valueOf("COMPANY_RISK_LEVEL_OVERRIDE")).isNotNull();
  }

  @Test
  void companyRiskLevelOverrideIsNotManual() {
    // COMPANY_RISK_LEVEL_OVERRIDE is specialist-created (Metabase action), not a
    // client-self-addable
    // check
    assertThat(AmlCheckType.COMPANY_RISK_LEVEL_OVERRIDE.isManual()).isFalse();
  }
}
