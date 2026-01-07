package ee.tuleva.onboarding.investment.position;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.investment.position.parser.SwedbankFundPositionParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionImportJobTest {

  @Mock private FundPositionSource source;
  @Spy private SwedbankFundPositionParser parser = new SwedbankFundPositionParser();
  @Mock private FundPositionRepository repository;

  private FundPositionImportService importService;
  private FundPositionImportJob job;

  @BeforeEach
  void setUp() {
    importService = new FundPositionImportService(repository);
    job = new FundPositionImportJob(source, parser, importService);
  }

  private static final String SAMPLE_CSV =
      """
      reporting_date\tfund_code\taccount_type\taccount_name\taccount_id\tquantity\tmarket_price\tcurrency\tmarket_value
      2026-01-05\tTUK75\tSECURITY\tISHARES DEV WLD ESG\tIE00BFG1TM61\t1000000\t33.5\tEUR\t33500000
      2026-01-05\tTUK75\tCASH\tOvernight Deposit\tRMP_KONTO_NR\t5000000\t1\tEUR\t5000000
      2026-01-05\tTUV100\tSECURITY\tISHARES USA ESG\tIE00BFNM3G45\t500000\t12\tEUR\t6000000
      """;

  @Test
  void importForDate_fullFlow_parsesAndSavesPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(repository.existsByReportingDate(date)).thenReturn(false);
    when(source.fetch(date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "ISHARES DEV WLD ESG"))
        .thenReturn(false);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "Overnight Deposit"))
        .thenReturn(false);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUV100", "ISHARES USA ESG"))
        .thenReturn(false);

    job.importForDate(date);

    verify(repository, times(3)).save(any(FundPosition.class));
  }

  @Test
  void importForDate_skipsExistingPositions() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(repository.existsByReportingDate(date)).thenReturn(false);
    when(source.fetch(date))
        .thenReturn(
            Optional.of(new ByteArrayInputStream(SAMPLE_CSV.getBytes(StandardCharsets.UTF_8))));
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "ISHARES DEV WLD ESG"))
        .thenReturn(true);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUK75", "Overnight Deposit"))
        .thenReturn(false);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            LocalDate.of(2026, 1, 5), "TUV100", "ISHARES USA ESG"))
        .thenReturn(false);

    job.importForDate(date);

    verify(repository, times(2)).save(any(FundPosition.class));
  }

  @Test
  void importForDate_handlesEmptyFile() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(repository.existsByReportingDate(date)).thenReturn(false);
    when(source.fetch(date)).thenReturn(Optional.empty());

    job.importForDate(date);

    verify(repository, never()).save(any());
  }

  @Test
  void importForDate_skipsAlreadyImportedDate() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(repository.existsByReportingDate(date)).thenReturn(true);

    job.importForDate(date);

    verify(source, never()).fetch(any());
    verify(repository, never()).save(any());
  }

  @Test
  void runImport_processesMultipleDays() {
    when(repository.existsByReportingDate(any())).thenReturn(true);

    job.runImport();

    verify(repository, times(7)).existsByReportingDate(any());
    verify(source, never()).fetch(any());
  }

  @Test
  void runImport_continuesOnError() {
    when(repository.existsByReportingDate(any())).thenReturn(false);
    when(source.fetch(any())).thenThrow(new RuntimeException("S3 error"));

    job.runImport();

    verify(source, times(7)).fetch(any());
  }

  @Test
  void importForDate_throwsRuntimeException_whenParsingFails() {
    LocalDate date = LocalDate.of(2026, 1, 5);
    when(repository.existsByReportingDate(date)).thenReturn(false);
    InputStream failingStream =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("Stream error");
          }
        };
    when(source.fetch(date)).thenReturn(Optional.of(failingStream));

    assertThatThrownBy(() -> job.importForDate(date))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Fund position import failed");
  }
}
