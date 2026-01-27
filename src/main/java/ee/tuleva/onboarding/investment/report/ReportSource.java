package ee.tuleva.onboarding.investment.report;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReportSource {

  ReportProvider getProvider();

  List<ReportType> getSupportedReportTypes();

  Optional<InputStream> fetch(ReportType reportType, LocalDate date);

  String getBucket();

  String getKey(ReportType reportType, LocalDate date);
}
