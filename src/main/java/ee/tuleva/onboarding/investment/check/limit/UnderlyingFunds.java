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

  // EODHD's total assets are in the fund's currency, not the listing currency it reports
  // (General::CurrencyCode): the XETRA listing of a USD fund trades in EUR, but its total assets
  // come in USD. So the size is converted from the instrument's fund currency.
  SizeInEur sizeInEur(String isin, LocalDate positionDate) {
    var instrument = instrumentReferenceService.findByIsin(isin);
    var ticker = instrument.map(InstrumentReference::getEodhdTicker);
    if (ticker.isEmpty()) {
      return new SizeInEur.Unknown("no EODHD ticker in instruments");
    }
    var fundCurrency = instrument.map(InstrumentReference::getFundCurrency);
    if (fundCurrency.isEmpty()) {
      return new SizeInEur.Unknown("no fund currency in instruments");
    }
    return switch (fundSizeClient.fetch(ticker.get())) {
      case FundSize.Unavailable unavailable -> new SizeInEur.Unknown(unavailable.reason());
      case FundSize.Reported reported -> freshEnough(reported, fundCurrency.get(), positionDate);
    };
  }

  private SizeInEur freshEnough(
      FundSize.Reported reported, String fundCurrency, LocalDate positionDate) {
    var updatedAt = reported.updatedAt();
    if (updatedAt == null) {
      return new SizeInEur.Unknown("EODHD gives no update date for the fund size");
    }
    if (updatedAt.isBefore(positionDate.withDayOfMonth(1))) {
      return new SizeInEur.Unknown(
          "EODHD last updated the fund size on %s, before %s began"
              .formatted(updatedAt, YearMonth.from(positionDate)));
    }
    return inEur(reported.amount(), fundCurrency, updatedAt);
  }

  private SizeInEur inEur(BigDecimal amount, String currency, LocalDate updatedAt) {
    return switch (currency) {
      case EUR -> new SizeInEur.Known(amount, amount, currency, updatedAt);
      case USD ->
          fundValueProvider
              .getLatestValue(EODHD_EUR_USD_STORAGE_KEY, LocalDate.now(clock))
              .<SizeInEur>map(
                  rate ->
                      new SizeInEur.Known(
                          amount.divide(rate.value(), 2, RoundingMode.HALF_UP),
                          amount,
                          currency,
                          updatedAt))
              .orElseGet(() -> noEurRate(currency));
      default -> noEurRate(currency);
    };
  }

  private static SizeInEur noEurRate(String currency) {
    return new SizeInEur.Unknown("no EUR rate for a fund size in " + currency);
  }

  sealed interface SizeInEur {

    record Known(
        BigDecimal amount,
        BigDecimal reportedAmount,
        String reportedCurrency,
        LocalDate reportedUpdatedAt)
        implements SizeInEur {}

    record Unknown(String reason) implements SizeInEur {}
  }
}
