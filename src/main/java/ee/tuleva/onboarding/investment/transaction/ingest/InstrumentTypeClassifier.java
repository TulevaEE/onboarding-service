package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;

import ee.tuleva.onboarding.investment.transaction.InstrumentType;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import org.springframework.stereotype.Component;

@Component
class InstrumentTypeClassifier {

  InstrumentType classify(TransactionOrder order) {
    InstrumentType type = order.getInstrumentType();
    return type == null ? FUND : type;
  }

  boolean isEtf(TransactionOrder order) {
    return classify(order) == ETF;
  }
}
