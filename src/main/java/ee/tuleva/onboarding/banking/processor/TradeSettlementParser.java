package ee.tuleva.onboarding.banking.processor;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeSettlementParser {

  private final InstrumentReferenceService instrumentReferenceService;

  public record TradeSettlementInfo(InstrumentReference ticker, BigDecimal units) {}

  public Optional<TradeSettlementInfo> parse(String remittanceInfo) {
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

    Optional<InstrumentReference> instrument =
        instrumentReferenceService
            .findByTicker(ticker)
            .or(() -> instrumentReferenceService.findByBloombergTicker(ticker));

    return instrument.map(
        found -> new TradeSettlementInfo(found, new BigDecimal(segments[2].trim())));
  }
}
