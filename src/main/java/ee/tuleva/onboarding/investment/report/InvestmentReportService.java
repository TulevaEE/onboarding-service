package ee.tuleva.onboarding.investment.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentReportService {

  private final InvestmentReportRepository repository;
  private final CsvToJsonConverter csvConverter;

  @Transactional
  public InvestmentReport saveReport(
      ReportProvider provider,
      ReportType reportType,
      LocalDate reportDate,
      InputStream csvStream,
      char delimiter,
      int headerRowIndex,
      Map<String, Object> metadata) {

    byte[] csvBytes = readAllBytes(csvStream);
    List<Map<String, Object>> rawData =
        csvConverter.convert(new ByteArrayInputStream(csvBytes), delimiter, headerRowIndex);

    Optional<InvestmentReport> existing =
        repository.findByProviderAndReportTypeAndReportDate(provider, reportType, reportDate);

    if (existing.isPresent()) {
      log.info(
          "Report already exists, updating: provider={}, reportType={}, reportDate={}",
          provider,
          reportType,
          reportDate);
      InvestmentReport report = existing.get();
      report.setRawData(rawData);
      report.setMetadata(metadata);
      return repository.save(report);
    }

    InvestmentReport report =
        InvestmentReport.builder()
            .provider(provider)
            .reportType(reportType)
            .reportDate(reportDate)
            .rawData(rawData)
            .metadata(metadata)
            .createdAt(Instant.now())
            .build();

    log.info(
        "Saving new report: provider={}, reportType={}, reportDate={}, rowCount={}",
        provider,
        reportType,
        reportDate,
        rawData.size());

    return repository.save(report);
  }

  public Optional<InvestmentReport> getReport(
      ReportProvider provider, ReportType reportType, LocalDate reportDate) {
    return repository.findByProviderAndReportTypeAndReportDate(provider, reportType, reportDate);
  }

  private byte[] readAllBytes(InputStream stream) {
    try {
      return stream.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read CSV stream", e);
    }
  }
}
