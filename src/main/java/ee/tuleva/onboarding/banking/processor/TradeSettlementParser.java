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

  public record TradeSettlementInfo(
      String isin, String ticker, String displayName, BigDecimal units) {}

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

    return instrument.map(found -> settlementInfo(found, new BigDecimal(segments[2].trim())));
  }

  private static TradeSettlementInfo settlementInfo(
      InstrumentReference instrument, BigDecimal units) {
    return new TradeSettlementInfo(
        instrument.getIsin(), shortTicker(instrument), instrument.getDisplayName(), units);
  }

  private static String shortTicker(InstrumentReference instrument) {
    var yahooTicker = instrument.getYahooTicker();
    if (yahooTicker == null) {
      throw new IllegalStateException(
          "Instrument has no yahoo ticker to settle a trade against: isin=%s, displayName=%s"
              .formatted(instrument.getIsin(), instrument.getDisplayName()));
    }
    return yahooTicker.split("\\.")[0];
  }
}
