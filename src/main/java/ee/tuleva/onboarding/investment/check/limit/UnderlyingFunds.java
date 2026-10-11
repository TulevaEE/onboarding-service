package ee.tuleva.onboarding.investment.check.limit;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.instrument.InstrumentReference;
import ee.tuleva.onboarding.instrument.InstrumentReferenceService;
import ee.tuleva.onboarding.investment.check.limit.EODHDFundSizeClient.FundSize;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class UnderlyingFunds {

  static final String EODHD_EUR_USD_STORAGE_KEY = "EURUSD.FOREX";
  private static final String EUR = "EUR";
  private static final String USD = "USD";

  private final InstrumentReferenceService instrumentReferenceService;
  private final EODHDFundSizeClient fundSizeClient;
  private final FundValueProvider fundValueProvider;
  private final Clock clock;

  String name(String isin, String nameWhenUnknown) {
    return instrumentReferenceService
        .findByIsin(isin)
        .map(InstrumentReference::getDisplayName)
        .orElse(nameWhenUnknown);
  }

  SizeInEur sizeInEur(String isin, LocalDate positionDate) {
    var ticker =
        instrumentReferenceService.findByIsin(isin).map(InstrumentReference::getEodhdTicker);
    if (ticker.isEmpty()) {
      return new SizeInEur.Unknown("no EODHD ticker in instruments");
    }
    return switch (fundSizeClient.fetch(ticker.get())) {
      case FundSize.Unavailable unavailable -> new SizeInEur.Unknown(unavailable.reason());
      case FundSize.Reported reported -> freshEnough(reported, positionDate);
    };
  }

  private SizeInEur freshEnough(FundSize.Reported reported, LocalDate positionDate) {
    var updatedAt = reported.updatedAt();
    if (updatedAt == null) {
      return new SizeInEur.Unknown("EODHD gives no update date for the fund size");
    }
    if (updatedAt.isBefore(positionDate.withDayOfMonth(1))) {
      return new SizeInEur.Unknown(
          "EODHD last updated the fund size on %s, before %s began"
              .formatted(updatedAt, YearMonth.from(positionDate)));
    }
    return inEur(reported);
  }

  private SizeInEur inEur(FundSize.Reported reported) {
    return switch (reported.listingCurrency()) {
      case EUR -> new SizeInEur.Known(reported.amount(), reported);
      case USD ->
          fundValueProvider
              .getLatestValue(EODHD_EUR_USD_STORAGE_KEY, LocalDate.now(clock))
              .<SizeInEur>map(
                  rate ->
                      new SizeInEur.Known(
                          reported.amount().divide(rate.value(), 2, RoundingMode.HALF_UP),
                          reported))
              .orElseGet(() -> noEurRate(reported));
      default -> noEurRate(reported);
    };
  }

  private static SizeInEur noEurRate(FundSize.Reported reported) {
    return new SizeInEur.Unknown("no EUR rate for a fund size in " + reported.listingCurrency());
  }

  sealed interface SizeInEur {

    record Known(BigDecimal amount, FundSize.Reported asReported) implements SizeInEur {}

    record Unknown(String reason) implements SizeInEur {}
  }
}
