package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.COMPANY_PARTIES;
import static ee.tuleva.onboarding.kyb.KybKycStatus.REJECTED;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.boardMemberOwner;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.KybCheck;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompanyPartiesScreenerTest {

  private final CompanyPartiesScreener screener = new CompanyPartiesScreener();

  @Test
  void emitsASuccessfulCompanyPartiesCheckCarryingTheParties() {
    var owner = boardMemberOwner("38888888888", 100.0).build();

    var result = screener.screen(companyWith(owner));

    assertThat(result)
        .containsExactly(
            new KybCheck(COMPANY_PARTIES, true, CompanyPartiesMetadata.of(List.of(owner))));
  }

  @Test
  void succeedsEvenWhenThePartiesWouldFailOtherChecks() {
    var rejectedOwner = boardMemberOwner("38888888888", 30.0).kycStatus(REJECTED).build();

    var result = screener.screen(companyWith(rejectedOwner));

    assertThat(result).extracting(KybCheck::success).containsExactly(true);
  }
}
