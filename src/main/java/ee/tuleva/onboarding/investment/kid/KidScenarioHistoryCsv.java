package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.config.http.CsvDownload.ESTONIAN_DATE;

import ee.tuleva.onboarding.config.http.CsvDownload;
import java.util.List;

record KidScenarioHistoryCsv(List<PublishedScenario> scenarios) {

  private static final List<String> HEADER =
      List.of(
          "Seisuga",
          "Hoidmisperiood (aastat)",
          "Stsenaarium",
          "Summa (EUR)",
          "Keskmine aastane tootlus (%)");
  private static final int FRACTION_TO_PERCENT = 2;

  byte[] toBytes() {
    return CsvDownload.bytes(
        HEADER, scenarios.stream().map(KidScenarioHistoryCsv::asPrinted).toList());
  }

  private static List<String> asPrinted(PublishedScenario scenario) {
    return List.of(
        ESTONIAN_DATE.format(scenario.month().atEndOfMonth()),
        String.valueOf(scenario.horizonYears()),
        scenario.scenario().asTheKidNamesIt(),
        scenario.amountEur().toPlainString(),
        scenario.averageReturnPerYear().movePointRight(FRACTION_TO_PERCENT).toPlainString());
  }
}
