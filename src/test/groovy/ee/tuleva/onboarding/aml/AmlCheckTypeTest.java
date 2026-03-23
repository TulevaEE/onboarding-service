package ee.tuleva.onboarding.aml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AmlCheckTypeTest {

  @Test
  void hasKybCheckTypes() {
    assertThat(AmlCheckType.valueOf("KYB_SOLE_MEMBER_OWNERSHIP")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_DUAL_MEMBER_OWNERSHIP")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_SOLE_BOARD_MEMBER_IS_OWNER")).isNotNull();
    assertThat(AmlCheckType.valueOf("KYB_COMPANY_ACTIVE")).isNotNull();
  }

  @Test
  void kybCheckTypesAreNotManual() {
    assertThat(AmlCheckType.KYB_SOLE_MEMBER_OWNERSHIP.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_DUAL_MEMBER_OWNERSHIP.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_SOLE_BOARD_MEMBER_IS_OWNER.isManual()).isFalse();
    assertThat(AmlCheckType.KYB_COMPANY_ACTIVE.isManual()).isFalse();
  }
}
