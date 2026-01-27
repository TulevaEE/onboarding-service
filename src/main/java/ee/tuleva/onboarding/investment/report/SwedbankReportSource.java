package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class SwedbankReportSource extends AbstractReportSource {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public SwedbankReportSource(S3Client s3Client) {
    super(s3Client);
  }

  @Override
  public ReportProvider getProvider() {
    return SWEDBANK;
  }

  @Override
  public List<ReportType> getSupportedReportTypes() {
    return List.of(POSITIONS);
  }

  @Override
  public String getKey(ReportType reportType, LocalDate date) {
    return "portfolio/" + date.format(DATE_FORMAT) + ".csv";
  }
}
