package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import org.junit.jupiter.api.Test;

class InstrumentTypeClassifierTest {

  private final InstrumentTypeClassifier classifier = new InstrumentTypeClassifier();

  @Test
  void etfOrder_classifiesAsEtf() {
    TransactionOrder order = TransactionOrder.builder().instrumentType(ETF).build();
    assertThat(classifier.classify(order)).isEqualTo(ETF);
  }

  @Test
  void fundOrder_classifiesAsFund() {
    TransactionOrder order = TransactionOrder.builder().instrumentType(FUND).build();
    assertThat(classifier.classify(order)).isEqualTo(FUND);
  }

  @Test
  void isEtf_truthTable() {
    TransactionOrder etfOrder = TransactionOrder.builder().instrumentType(ETF).build();
    TransactionOrder fundOrder = TransactionOrder.builder().instrumentType(FUND).build();
    assertThat(classifier.isEtf(etfOrder)).isTrue();
    assertThat(classifier.isEtf(fundOrder)).isFalse();
  }

  @Test
  void nullInstrumentType_defaultsToFund() {
    // Defensive: orders without an explicit instrument type are conservatively
    // treated as FUND so they are skipped from the NAV check (avoids false-positive
    // ExecutionMismatchEvents on unclassified rows).
    TransactionOrder order =
        TransactionOrder.builder().instrumentType((InstrumentType) null).build();
    assertThat(classifier.classify(order)).isEqualTo(FUND);
    assertThat(classifier.isEtf(order)).isFalse();
  }
}
