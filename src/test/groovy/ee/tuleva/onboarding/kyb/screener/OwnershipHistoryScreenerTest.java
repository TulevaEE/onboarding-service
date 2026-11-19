package ee.tuleva.onboarding.kyb.screener;

import static ee.tuleva.onboarding.kyb.KybCheckType.OWNERSHIP_HISTORY;
import static ee.tuleva.onboarding.kyb.KybTestFixtures.companyWith;
import static ee.tuleva.onboarding.kyb.OwnershipRole.BENEFICIAL_OWNER;
import static ee.tuleva.onboarding.kyb.OwnershipRole.SHAREHOLDER;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.kyb.KybCheck;
import ee.tuleva.onboarding.kyb.KybOwnership;
import ee.tuleva.onboarding.kyb.RegisterHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OwnershipHistoryScreenerTest {

  private final OwnershipHistoryScreener screener = new OwnershipHistoryScreener();

  @Test
  void recordsEveryShareholdingAndBeneficialOwnershipTheRegisterListsEndedOnesToo() {
    var current = new KybOwnership("38888888888", SHAREHOLDER, LocalDate.of(2024, 3, 1), null);
    var former =
        new KybOwnership(
            "48888888888", SHAREHOLDER, LocalDate.of(2019, 5, 2), LocalDate.of(2024, 2, 29));
    var beneficialOwner =
        new KybOwnership("38888888888", BENEFICIAL_OWNER, LocalDate.of(2024, 3, 4), null);

    var result =
        screener.screen(
            companyWith(
                new RegisterHistory(
                    List.of(former, beneficialOwner, current), List.of(), List.of())));

    assertThat(result)
        .containsExactly(
            new KybCheck(
                OWNERSHIP_HISTORY,
                true,
                Map.of(
                    "ownerships",
                    List.of(
                        Map.of(
                            "code",
                            "38888888888",
                            "role",
                            "SHAREHOLDER",
                            "startDate",
                            "2024-03-01"),
                        Map.of(
                            "code",
                            "38888888888",
                            "role",
                            "BENEFICIAL_OWNER",
                            "startDate",
                            "2024-03-04"),
                        Map.of(
                            "code",
                            "48888888888",
                            "role",
                            "SHAREHOLDER",
                            "startDate",
                            "2019-05-02",
                            "endDate",
                            "2024-02-29")))));
  }

  @Test
  void leavesOutTheKeysOfWhatTheRegisterDoesNotGive() {
    var undated = new KybOwnership(null, BENEFICIAL_OWNER, null, null);

    var result =
        screener.screen(companyWith(new RegisterHistory(List.of(undated), List.of(), List.of())));

    assertThat(result)
        .containsExactly(
            new KybCheck(
                OWNERSHIP_HISTORY,
                true,
                Map.of("ownerships", List.of(Map.of("role", "BENEFICIAL_OWNER")))));
  }

  @Test
  void recordsNoOwnershipsForARegisterListingNone() {
    var result = screener.screen(companyWith(new RegisterHistory(List.of(), List.of(), List.of())));

    assertThat(result)
        .containsExactly(new KybCheck(OWNERSHIP_HISTORY, true, Map.of("ownerships", List.of())));
  }
}
