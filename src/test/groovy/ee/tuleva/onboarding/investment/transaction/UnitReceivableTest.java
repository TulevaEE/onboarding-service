package ee.tuleva.onboarding.investment.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class UnitReceivableTest {

  @Test
  void aFundThatMayInvestCashKnownToArrive_countsTheWholeReportedReceivable() {
    var unitReceivable = new UnitReceivable(new BigDecimal("8000"), new BigDecimal("5000"), true);

    assertThat(unitReceivable.dueOnTopOfReportCash()).isEqualByComparingTo("8000");
  }

  @Test
  void aFundThatMayInvestOnlyCashOnItsAccount_countsOnlyWhatHasArrivedSinceTheReport() {
    var unitReceivable = new UnitReceivable(new BigDecimal("8000"), new BigDecimal("5000"), false);

    assertThat(unitReceivable.dueOnTopOfReportCash()).isEqualByComparingTo("5000");
  }

  @Test
  void aFundThatMayInvestOnlyCashOnItsAccount_neverCountsMoreThanTheReportedReceivable() {
    var unitReceivable = new UnitReceivable(new BigDecimal("8000"), new BigDecimal("9000"), false);

    assertThat(unitReceivable.dueOnTopOfReportCash()).isEqualByComparingTo("8000");
  }
}
