package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.FUND;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.HELD_QUANTITY;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.INSTRUMENT_ISIN;
import static ee.tuleva.onboarding.investment.transaction.portfolio.CostBasisOversellEvents.SELL_QUANTITY;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

final class CostBasisOversellAlertText {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private CostBasisOversellAlertText() {}

  static String found(Map<String, Object> payload, LocalDate asOfDate) {
    return oversell(
        payload,
        asOfDate,
        "Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see; öine"
            + " ümberarvutus parandab ülejäänu");
  }

  static String unrecorded(Map<String, Object> payload, LocalDate asOfDate) {
    return oversell(
        payload,
        asOfDate,
        "Leia puuduv ost või vale müügikogus tehinguregistris ja paranda see; hoiatust ei"
            + " õnnestunud salvestada, seega öine ümberarvutus seda päeva ise ei korda ja"
            + " pärast parandust vajab soetusmaksumus käsitsi ümberarvutust");
  }

  private static String oversell(Map<String, Object> payload, LocalDate asOfDate, String action) {
    return String.join(
        "\n",
        "🔴 "
            + subject(payload, asOfDate)
            + " müük "
            + payload.get(SELL_QUANTITY)
            + " ühikut, teadaolev positsioon "
            + payload.get(HELD_QUANTITY),
        "Fondi soetusmaksumus on sellest päevast alates vale, kuni see on parandamata",
        action);
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
