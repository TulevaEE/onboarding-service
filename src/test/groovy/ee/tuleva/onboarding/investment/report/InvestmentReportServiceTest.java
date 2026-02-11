package ee.tuleva.onboarding.investment.report;

import static ee.tuleva.onboarding.investment.report.ReportProvider.SWEDBANK;
import static ee.tuleva.onboarding.investment.report.ReportType.POSITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentReportServiceTest {

  @Mock private InvestmentReportRepository repository;
  @Spy private CsvToJsonConverter csvConverter = new CsvToJsonConverter();
  @InjectMocks private InvestmentReportService service;

  @Test
  void saveReport_createsNewReport() {
    when(repository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(repository.save(any()))
        .thenAnswer(
            invocation -> {
              InvestmentReport report = invocation.getArgument(0);
              report.setId(1L);
              return report;
            });

    String csv = "col1;col2\nval1;123";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    InvestmentReport result =
        service.saveReport(
            SWEDBANK,
            POSITIONS,
            LocalDate.of(2026, 1, 15),
            inputStream,
            ';',
            0,
            Map.of("filename", "test.csv"));

    assertThat(result.getProvider()).isEqualTo(SWEDBANK);
    assertThat(result.getReportType()).isEqualTo(POSITIONS);
    assertThat(result.getReportDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    assertThat(result.getRawData()).hasSize(1);
    assertThat(result.getRawData().getFirst().get("col1")).isEqualTo("val1");
    assertThat(result.getMetadata().get("filename")).isEqualTo("test.csv");
    assertThat(result.getCreatedAt()).isNotNull();
  }

  @Test
  void saveReport_updatesExistingReport() {
    InvestmentReport existing =
        InvestmentReport.builder()
            .id(1L)
            .provider(SWEDBANK)
            .reportType(POSITIONS)
            .reportDate(LocalDate.of(2026, 1, 15))
            .rawData(List.of())
            .metadata(Map.of())
            .build();

    when(repository.findByProviderAndReportTypeAndReportDate(any(), any(), any()))
        .thenReturn(Optional.of(existing));
    when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    String csv = "col1;col2\nnewval;456";
    var inputStream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

    InvestmentReport result =
        service.saveReport(
            SWEDBANK,
            POSITIONS,
            LocalDate.of(2026, 1, 15),
            inputStream,
            ';',
            0,
            Map.of("updated", "true"));

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getRawData()).hasSize(1);
    assertThat(result.getRawData().getFirst().get("col1")).isEqualTo("newval");
    assertThat(result.getMetadata().get("updated")).isEqualTo("true");
  }
}
