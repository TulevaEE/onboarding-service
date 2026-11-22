package ee.tuleva.onboarding.fund;

import static ee.tuleva.onboarding.config.http.CsvDownload.ESTONIAN_DATE;

import ee.tuleva.onboarding.config.http.CsvDownload;
import java.util.List;

record NavHistoryCsv(List<NavValueResponse> navHistory) {

  byte[] toBytes() {
    return CsvDownload.bytes(
        List.of("Kuupäev", "NAV (EUR)"),
        navHistory.stream()
            .map(row -> List.of(ESTONIAN_DATE.format(row.date()), row.value().toPlainString()))
            .toList());
  }
}
