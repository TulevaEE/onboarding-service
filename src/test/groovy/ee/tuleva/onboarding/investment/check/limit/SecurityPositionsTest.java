package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.investment.check.limit.SecurityPositions.mostRecentlyWrittenRowPerIsin;
import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.position.FundPosition;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPositionsTest {

  private static final Instant FIRST_IMPORT = Instant.parse("2026-03-02T18:00:00Z");
  private static final Instant RESEND = Instant.parse("2026-03-03T08:00:00Z");
  private static final Instant LATER_UPDATE = Instant.parse("2026-03-03T09:00:00Z");

  @Test
  void aRenamedResendKeepsOnlyTheRowWrittenLast() {
    var beforeResend = row(1L, "XS0000RESNT1", "2600000", FIRST_IMPORT, null);
    var afterResend = row(2L, "XS0000RESNT1", "2000000", RESEND, null);

    assertThat(mostRecentlyWrittenRowPerIsin(List.of(afterResend, beforeResend)))
        .containsExactly(afterResend);
  }

  @Test
  void aRowUpdatedAfterTheResendIsWrittenLast() {
    var updatedLater = row(1L, "XS0000RESNT1", "2000000", FIRST_IMPORT, LATER_UPDATE);
    var resent = row(2L, "XS0000RESNT1", "2600000", RESEND, null);

    assertThat(mostRecentlyWrittenRowPerIsin(List.of(updatedLater, resent)))
        .containsExactly(updatedLater);
  }

  @Test
  void rowsWrittenAtTheSameInstantGoToTheOneInsertedLast() {
    var insertedFirst = row(1L, "XS0000RESNT1", "2600000", RESEND, null);
    var insertedLast = row(2L, "XS0000RESNT1", "2000000", RESEND, null);

    assertThat(mostRecentlyWrittenRowPerIsin(List.of(insertedLast, insertedFirst)))
        .containsExactly(insertedLast);
  }

  @Test
  void instrumentsReportedOnceAreAllKeptInTheirOrder() {
    var first = row(1L, "XS0000SINGL1", "100000", FIRST_IMPORT, null);
    var resent = row(3L, "XS0000RESNT1", "2000000", RESEND, null);
    var second = row(2L, "XS0000SINGL2", "200000", FIRST_IMPORT, null);
    var resentBefore = row(4L, "XS0000RESNT1", "2600000", FIRST_IMPORT, null);

    assertThat(mostRecentlyWrittenRowPerIsin(List.of(first, resent, second, resentBefore)))
        .containsExactly(first, resent, second);
  }

  @Test
  void aRowWithoutAnIsinIsLeftOutSinceNoLimitCanMatchIt() {
    var withIsin = row(1L, "XS0000SINGL1", "100000", FIRST_IMPORT, null);
    var withoutIsin = row(2L, null, "50000", FIRST_IMPORT, null);

    assertThat(mostRecentlyWrittenRowPerIsin(List.of(withIsin, withoutIsin)))
        .containsExactly(withIsin);
  }

  private static FundPosition row(
      Long id, String isin, String marketValue, Instant createdAt, Instant updatedAt) {
    return FundPosition.builder()
        .id(id)
        .fund(TUK75)
        .accountType(SECURITY)
        .accountName("row " + id)
        .accountId(isin)
        .marketValue(new BigDecimal(marketValue))
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }
}
