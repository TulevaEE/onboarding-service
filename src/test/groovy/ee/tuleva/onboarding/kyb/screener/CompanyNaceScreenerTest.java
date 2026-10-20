package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.CompanyStatus.R;
import static ee.tuleva.onboarding.kyb.KybCheckType.HIGH_RISK_NACE;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.totalOwnershipOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CompanyNaceScreenerTest {

  private final CompanyNaceScreener screener = new CompanyNaceScreener();

  @Test
  void companyWhoseActivitiesAreAllUnlistedPasses() {
    var data = companyWithActivities(List.of("62011", "70201"));

    var results = screener.screen(data);

    assertThat(results)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(HIGH_RISK_NACE, true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"64321", "66199", "66301", "92001", "47791"})
  void anyHighRiskActivityFailsEvenBesideAnUnlistedMainActivity(String highRiskCode) {
    var data = companyWithActivities(List.of("62011", highRiskCode));

    var results = screener.screen(data);

    assertThat(results)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(HIGH_RISK_NACE, false));
  }

  @Test
  void companyWithNoKnownActivityFails() {
    var data = companyWithActivities(List.of());

    var results = screener.screen(data);

    assertThat(results)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(HIGH_RISK_NACE, false));
  }

  @Test
  void metadataListsTheActivityCodes() {
    var data = companyWithActivities(List.of("62011", "92001"));

    var results = screener.screen(data);

    assertThat(results.getFirst().metadata())
        .isEqualTo(Map.of("activityCodes", List.of("62011", "92001")));
  }

  private KybCompanyData companyWithActivities(List<String> activityCodes) {
    var company =
        new CompanyDto(new RegistryCode("12345678"), "Test OÜ", activityCodes, LegalForm.OÜ);
    var person = boardMemberOwner("38501010001", 100.0).build();
    return new KybCompanyData(
        company,
        new PersonalCode("38501010001"),
        R,
        List.of(person),
        totalOwnershipOf(List.of(person)),
        new SelfCertification(true, true, true),
        "EE",
        "Harju maakond, Tallinn, Pärnu mnt 1",
        null,
        List.of());
  }
}
