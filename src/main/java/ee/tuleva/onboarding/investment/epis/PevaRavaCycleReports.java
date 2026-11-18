package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.investment.report.ReportType.R17_PEVA;
import static ee.tuleva.onboarding.investment.report.ReportType.R21_RAVA;

import ee.tuleva.onboarding.investment.report.ReportType;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record PevaRavaCycleReports(@Nullable Long r17ReportId, @Nullable Long r21ReportId) {

  public List<ReportType> missingReports() {
    List<ReportType> missing = new ArrayList<>();
    if (r17ReportId == null) {
      missing.add(R17_PEVA);
    }
    if (r21ReportId == null) {
      missing.add(R21_RAVA);
    }
    return List.copyOf(missing);
  }
}
