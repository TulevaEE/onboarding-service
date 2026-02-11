package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class SebReportSource extends AbstractReportSource {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public SebReportSource(S3Client s3Client) {
    super(s3Client);
  }

  @Override
  public ReportProvider getProvider() {
    return SEB;
  }

  @Override
  public List<ReportType> getSupportedReportTypes() {
    return List.of(POSITIONS, PENDING_TRANSACTIONS);
  }

  @Override
  public String getKey(ReportType reportType, LocalDate date) {
    String suffix =
        switch (reportType) {
          case POSITIONS -> "_positions.csv";
          case PENDING_TRANSACTIONS -> "_pending_transactions.csv";
        };
    return "seb/" + date.format(DATE_FORMAT) + suffix;
  }

  @Override
  public int getHeaderRowIndex() {
    return 5;
  }
}
