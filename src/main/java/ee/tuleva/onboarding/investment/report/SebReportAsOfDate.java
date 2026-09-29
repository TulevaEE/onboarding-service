package ee.tuleva.onboarding.investment.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SebReportAsOfDate {

  public LocalDate resolveOrFallBackToReportDate(
      ReportType reportType,
      LocalDate reportDate,
      Map<String, Object> metadata,
      List<Map<String, Object>> rawData) {
    LocalDate asOfDate = SebReportHeaders.asOfDate(metadata, rawData);
    if (asOfDate != null) {
      return asOfDate;
    }
    log.warn(
        "No usable 'As of' date in SEB report, falling back to report date: reportType={},"
            + " reportDate={}, unreadableValue={}",
        reportType,
        reportDate,
        SebReportHeaders.unreadableAsOfValue(metadata, rawData));
    return reportDate;
  }
}
