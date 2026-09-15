package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SebReportAsOfDate {

  private final ApplicationEventPublisher eventPublisher;

  public LocalDate resolveOrFallBackToReportDate(
      ReportType reportType,
      LocalDate reportDate,
      Map<String, Object> metadata,
      List<Map<String, Object>> rawData) {
    LocalDate asOfDate = SebReportHeaders.asOfDate(metadata, rawData);
    if (asOfDate != null) {
      return asOfDate;
    }
    String unreadableValue = SebReportHeaders.unreadableAsOfValue(metadata, rawData);
    log.warn(
        "No usable 'As of' date in SEB report, falling back to report date: reportType={},"
            + " reportDate={}, unreadableValue={}",
        reportType,
        reportDate,
        unreadableValue);
    eventPublisher.publishEvent(
        new MissingReportAsOfDateEvent(SEB, reportType, reportDate, unreadableValue));
    return reportDate;
  }
}
