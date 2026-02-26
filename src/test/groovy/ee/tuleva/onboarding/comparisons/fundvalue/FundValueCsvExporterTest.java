package ee.tuleva.onboarding.comparisons.fundvalue;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundValueCsvExporterTest {

  @Mock private FundValueRepository repository;

  @InjectMocks private FundValueCsvExporter exporter;

  @Test
  void exportToCsv_shouldWriteValidCsv_whenDataExists() {
    var isin = "EE3600109435";
    var startDate = LocalDate.of(2023, 1, 1);
    var endDate = LocalDate.of(2023, 1, 3);
    var values =
        List.of(
            new FundValue(isin, LocalDate.of(2023, 1, 1), new BigDecimal("100.00")),
            new FundValue(isin, LocalDate.of(2023, 1, 2), new BigDecimal("100.50")),
            new FundValue(isin, LocalDate.of(2023, 1, 3), new BigDecimal("101.25")));

    when(repository.findValuesBetweenDates(isin, startDate, endDate)).thenReturn(values);

    var outputStream = new ByteArrayOutputStream();
    exporter.exportToCsv(isin, startDate, endDate, outputStream);

    var csv = outputStream.toString(UTF_8);
    assertThat(csv).contains("date,nav");
    assertThat(csv).contains("2023-01-01,100.00");
    assertThat(csv).contains("2023-01-02,100.50");
    assertThat(csv).contains("2023-01-03,101.25");
  }

  @Test
  void exportToCsv_shouldThrowNotFoundException_whenNoDataExists() {
    var isin = "EE3600109435";
    var startDate = LocalDate.of(2023, 1, 1);
    var endDate = LocalDate.of(2023, 1, 3);

    when(repository.findValuesBetweenDates(isin, startDate, endDate)).thenReturn(List.of());

    assertThrows(
        NotFoundException.class,
        () -> exporter.exportToCsv(isin, startDate, endDate, new ByteArrayOutputStream()));
  }

  @Test
  void exportToCsv_shouldThrowNotFoundException_whenIsinDoesNotStartWithEE() {
    var isin = "LV1234567890";
    var startDate = LocalDate.of(2023, 1, 1);
    var endDate = LocalDate.of(2023, 1, 3);

    assertThrows(
        NotFoundException.class,
        () -> exporter.exportToCsv(isin, startDate, endDate, new ByteArrayOutputStream()));
  }

  @Test
  void exportToCsv_shouldThrowBadRequestException_whenStartDateAfterEndDate() {
    var isin = "EE3600109435";
    var startDate = LocalDate.of(2023, 1, 3);
    var endDate = LocalDate.of(2023, 1, 1);

    assertThrows(
        BadRequestException.class,
        () -> exporter.exportToCsv(isin, startDate, endDate, new ByteArrayOutputStream()));
  }

  @Test
  void exportToCsv_shouldFormatDatesCorrectly() {
    var isin = "EE3600109435";
    var startDate = LocalDate.of(2023, 12, 31);
    var endDate = LocalDate.of(2023, 12, 31);
    var values = List.of(new FundValue(isin, LocalDate.of(2023, 12, 31), new BigDecimal("99.99")));

    when(repository.findValuesBetweenDates(isin, startDate, endDate)).thenReturn(values);

    var outputStream = new ByteArrayOutputStream();
    exporter.exportToCsv(isin, startDate, endDate, outputStream);

    var csv = outputStream.toString(UTF_8);
    assertThat(csv).contains("2023-12-31");
  }

  @Test
  void exportToCsv_shouldFormatDecimalsCorrectly() {
    var isin = "EE3600109435";
    var startDate = LocalDate.of(2023, 1, 1);
    var endDate = LocalDate.of(2023, 1, 2);
    var values =
        List.of(
            new FundValue(isin, LocalDate.of(2023, 1, 1), new BigDecimal("100.12345")),
            new FundValue(isin, LocalDate.of(2023, 1, 2), new BigDecimal("0.5")));

    when(repository.findValuesBetweenDates(isin, startDate, endDate)).thenReturn(values);

    var outputStream = new ByteArrayOutputStream();
    exporter.exportToCsv(isin, startDate, endDate, outputStream);

    var csv = outputStream.toString(UTF_8);
    assertThat(csv).contains("100.12345");
    assertThat(csv).contains("0.5");
  }
}
