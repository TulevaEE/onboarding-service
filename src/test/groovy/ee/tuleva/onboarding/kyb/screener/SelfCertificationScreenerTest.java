package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.SELF_CERTIFICATION;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import ee.tuleva.onboarding.kyb.*;
import org.junit.jupiter.api.Test;

class SelfCertificationScreenerTest {

  private final SelfCertificationScreener screener = new SelfCertificationScreener();

  @Test
  void allThreeConfirmedPasses() {
    var data = companyWithCertification(true, true, true);

    var results = screener.screen(data);

    assertThat(results)
        .extracting(KybCheck::type, KybCheck::success)
        .containsExactly(tuple(SELF_CERTIFICATION, true));
  }

  @Test
  void operatesInEstoniaFalseFails() {
    var data = companyWithCertification(false, true, true);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void notSanctionedFalseFails() {
    var data = companyWithCertification(true, false, true);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void noHighRiskActivityFalseFails() {
    var data = companyWithCertification(true, true, false);

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void nullCertificationFails() {
    var data =
        companyWith((SelfCertification) null, boardMemberOwner("38501010001", 100.0).build());

    var results = screener.screen(data);

    assertThat(results.getFirst().success()).isFalse();
  }

  @Test
  void metadataContainsCertificationValues() {
    var data = companyWithCertification(true, false, true);

    var results = screener.screen(data);

    var metadata = results.getFirst().metadata();
    assertThat(metadata)
        .containsEntry("operatesInEstonia", true)
        .containsEntry("notSanctioned", false)
        .containsEntry("noHighRiskActivity", true);
  }

  private KybCompanyData companyWithCertification(
      boolean operatesInEstonia, boolean notSanctioned, boolean noHighRiskActivity) {
    return companyWith(
        new SelfCertification(operatesInEstonia, notSanctioned, noHighRiskActivity),
        boardMemberOwner("38501010001", 100.0).build());
  }
}
