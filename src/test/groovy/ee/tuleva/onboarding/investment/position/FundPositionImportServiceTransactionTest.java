package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.CASH;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Transactional(propagation = NOT_SUPPORTED)
@Import({
  FundPositionImportService.class,
  FundPositionImportServiceTransactionTest.FixedClock.class
})
class FundPositionImportServiceTransactionTest {

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 10, 20);

  @Autowired private FundPositionImportService importService;
  @Autowired private FundPositionRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  @Test
  void aRowThatCannotBeWrittenLeavesNoneOfTheDaysPositionsBehind() {
    var cash = position("Cash account in SEB Pank").fund(TUK75).build();
    var withoutFund = position("Total payables of unsettled transactions").build();

    assertThatThrownBy(() -> importService.upsertPositions(List.of(cash, withoutFund)))
        .isInstanceOf(RuntimeException.class);

    assertThat(repository.findAll()).isEmpty();
  }

  private static FundPosition.FundPositionBuilder position(String accountName) {
    return FundPosition.builder()
        .navDate(NAV_DATE)
        .reportDate(NAV_DATE)
        .accountType(CASH)
        .accountName(accountName)
        .marketValue(new BigDecimal("1000.00"));
  }

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-21T07:00:00Z"), ZoneOffset.UTC);
    }
  }
}
