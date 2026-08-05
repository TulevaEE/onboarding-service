package ee.tuleva.onboarding.account.transaction;

import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.CONTRIBUTION_CASH_WORKPLACE;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.OTHER;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.REFUND;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.SUBTRACTION;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.TRANSFER_FROM_PIK;
import static ee.tuleva.onboarding.epis.cashflows.CashFlow.Type.TRANSFER_TO_PIK;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.epis.cashflows.CashFlow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TransactionTest {

  @ParameterizedTest
  @EnumSource(
      value = CashFlow.Type.class,
      names = {"CONTRIBUTION_CASH", "CONTRIBUTION_CASH_WORKPLACE", "CONTRIBUTION"})
  void contributionsAreAcquisitions(CashFlow.Type type) {
    assertThat(Transaction.builder().type(type).build().isAcquisition()).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = CashFlow.Type.class,
      names = {"SUBTRACTION", "CASH", "REFUND", "TRANSFER_TO_PIK", "TRANSFER_FROM_PIK", "OTHER"})
  void everythingElseIsNotAnAcquisition(CashFlow.Type type) {
    assertThat(Transaction.builder().type(type).build().isAcquisition()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(CashFlow.Type.class)
  void everyTypeIsPinnedByTheseTests(CashFlow.Type type) {
    assertThat(type)
        .isIn(
            CONTRIBUTION_CASH,
            CONTRIBUTION_CASH_WORKPLACE,
            CONTRIBUTION,
            SUBTRACTION,
            CASH,
            REFUND,
            TRANSFER_TO_PIK,
            TRANSFER_FROM_PIK,
            OTHER);
  }
}
