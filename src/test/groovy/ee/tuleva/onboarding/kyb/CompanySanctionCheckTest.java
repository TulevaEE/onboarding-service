package ee.tuleva.onboarding.kyb;

import static ee.tuleva.onboarding.aml.ScreeningOutcome.CLEAR;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.PEP_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.SANCTION_HIT;
import static ee.tuleva.onboarding.aml.ScreeningOutcome.UNAVAILABLE;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PEP;
import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_SANCTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.aml.sanctions.ScreenedCompany;
import ee.tuleva.onboarding.kyb.screener.CompanySanctionScreener;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompanySanctionCheckTest {

  private static final String REGISTRY_CODE = "12345678";
  private static final String NAME = "MAASIKAS OÜ";
  private static final ScreenedCompany COMPANY = new ScreenedCompany(NAME, REGISTRY_CODE);

  private final CompanySanctionScreener companySanctionScreener =
      mock(CompanySanctionScreener.class);

  private final CompanySanctionCheck check = new CompanySanctionCheck(companySanctionScreener);

  @Test
  void aFailedSanctionCheckIsASanctionHit() {
    given(companySanctionScreener.screen(COMPANY))
        .willReturn(List.of(check(COMPANY_SANCTION, false), check(COMPANY_PEP, false)));

    assertThat(check.screeningOutcome(REGISTRY_CODE, NAME)).isEqualTo(SANCTION_HIT);
  }

  @Test
  void onlyAFailedPepCheckIsAPepHit() {
    given(companySanctionScreener.screen(COMPANY))
        .willReturn(List.of(check(COMPANY_SANCTION, true), check(COMPANY_PEP, false)));

    assertThat(check.screeningOutcome(REGISTRY_CODE, NAME)).isEqualTo(PEP_HIT);
  }

  @Test
  void passedChecksAreClear() {
    given(companySanctionScreener.screen(COMPANY))
        .willReturn(List.of(check(COMPANY_SANCTION, true), check(COMPANY_PEP, true)));

    assertThat(check.screeningOutcome(REGISTRY_CODE, NAME)).isEqualTo(CLEAR);
  }

  @Test
  void aScreeningWithoutASanctionCheckIsUnavailable() {
    given(companySanctionScreener.screen(COMPANY)).willReturn(List.of(check(COMPANY_PEP, true)));

    assertThat(check.screeningOutcome(REGISTRY_CODE, NAME)).isEqualTo(UNAVAILABLE);
  }

  @Test
  void aFailedScreeningIsUnavailable() {
    willThrow(new IllegalStateException("screening down"))
        .given(companySanctionScreener)
        .screen(COMPANY);

    assertThat(check.screeningOutcome(REGISTRY_CODE, NAME)).isEqualTo(UNAVAILABLE);
  }

  @Test
  void aCompanyWithoutANameCannotBeScreened() {
    assertThat(check.screeningOutcome(REGISTRY_CODE, null)).isEqualTo(UNAVAILABLE);
    assertThat(check.screeningOutcome(REGISTRY_CODE, " ")).isEqualTo(UNAVAILABLE);
    verifyNoInteractions(companySanctionScreener);
  }

  private static KybCheck check(KybCheckType type, boolean success) {
    return new KybCheck(type, success, Map.of());
  }
}
