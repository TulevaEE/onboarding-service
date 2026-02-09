package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class TradeSettlementParser {

  Optional<FundTicker> parse(String remittanceInfo) {
    if (remittanceInfo == null || remittanceInfo.isEmpty()) {
      return Optional.empty();
    }

    String[] segments = remittanceInfo.split("/");
    if (segments.length < 2) {
      return Optional.empty();
    }

    String tickerSegment = segments[1].trim();
    int spaceIndex = tickerSegment.indexOf(' ');
    String ticker = spaceIndex > 0 ? tickerSegment.substring(0, spaceIndex) : tickerSegment;

    return FundTicker.findByTicker(ticker).or(() -> FundTicker.findByBloombergTicker(ticker));
  }
}
