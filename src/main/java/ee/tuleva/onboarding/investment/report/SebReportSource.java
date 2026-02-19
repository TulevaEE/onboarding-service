package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.investment.report.ReportType.PENDING_TRANSACTIONS;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  @Override
  public Map<String, Object> extractCsvMetadata(byte[] csvBytes) {
    String content = new String(csvBytes, StandardCharsets.UTF_8);
    String[] lines = content.split("\n", getHeaderRowIndex() + 1);

    Map<String, Object> metadata = new HashMap<>();
    for (int i = 0; i < Math.min(lines.length, getHeaderRowIndex()); i++) {
      String[] columns = lines[i].split(";", 3);
      if (columns.length < 2) {
        continue;
      }
      String label = columns[0].trim();
      String value = columns[1].trim();
      if ("Sent:".equals(label)) {
        metadata.put("sentDate", value);
      } else if ("As of:".equals(label)) {
        metadata.put("asOfDate", value);
      }
    }
    return metadata;
  }
}
