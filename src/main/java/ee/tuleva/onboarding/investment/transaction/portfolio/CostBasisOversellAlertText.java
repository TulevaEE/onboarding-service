package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.FUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.HELD_QUANTITY;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.INSTRUMENT_ISIN;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellAlerter.SELL_QUANTITY;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

final class CostBasisOversellAlertText {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private CostBasisOversellAlertText() {}

  static String found(Map<String, Object> payload, LocalDate asOfDate) {
    return String.join(
        "\n",
        "🔴 "
            + subject(payload, asOfDate)
            + " müük "
            + payload.get(SELL_QUANTITY)
            + " ühikut, teadaolev positsioon "
            + payload.get(HELD_QUANTITY),
        "Fondi soetusmaksumus on sellest päevast alates vale, kuni see on parandamata",
        "Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see; öine"
            + " ümberarvutus parandab ülejäänu");
  }

  static String cleared(Map<String, Object> payload, LocalDate asOfDate) {
    return "✅ "
        + subject(payload, asOfDate)
        + " müük ei ületa enam positsiooni, soetusmaksumus on ümber arvutatud";
  }

  private static String subject(Map<String, Object> payload, LocalDate asOfDate) {
    return payload.get(FUND)
        + " soetusmaksumus "
        + DATE.format(asOfDate)
        + ": "
        + payload.get(INSTRUMENT_ISIN);
  }
}
