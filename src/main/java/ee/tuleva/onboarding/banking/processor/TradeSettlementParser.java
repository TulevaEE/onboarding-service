package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.comparisons.fundvalue.retrieval.FundTicker;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class TradeSettlementParser {

  record TradeSettlementInfo(FundTicker ticker, BigDecimal units) {}

  Optional<TradeSettlementInfo> parse(String remittanceInfo) {
    if (remittanceInfo == null || remittanceInfo.isEmpty()) {
      return Optional.empty();
    }

    String[] segments = remittanceInfo.split("/");
    if (segments.length < 3) {
      return Optional.empty();
    }

    String tickerSegment = segments[1].trim();
    int spaceIndex = tickerSegment.indexOf(' ');
    String ticker = spaceIndex > 0 ? tickerSegment.substring(0, spaceIndex) : tickerSegment;

    Optional<FundTicker> fundTicker =
        FundTicker.findByTicker(ticker).or(() -> FundTicker.findByBloombergTicker(ticker));

    return fundTicker.map(ft -> new TradeSettlementInfo(ft, new BigDecimal(segments[2].trim())));
  }
}
