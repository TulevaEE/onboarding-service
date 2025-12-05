package ee.tuleva.onboarding.comparisons.fundvalue;

import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundValueCsvExporter {

  private final FundValueRepository repository;

  public void exportToCsv(
      String isin, LocalDate startDate, LocalDate endDate, OutputStream outputStream) {
    validateIsin(isin);
    validateDateRange(startDate, endDate);

    List<FundValue> values = repository.findValuesBetweenDates(isin, startDate, endDate);

    if (values.isEmpty()) {
      throw new NotFoundException("No data found for ISIN: " + isin);
    }

    writeCsv(values, outputStream);
  }

  private void validateIsin(String isin) {
    if (!isin.startsWith("EE")) {
      throw new NotFoundException("No data found for ISIN: " + isin);
    }
  }

  private void validateDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw new BadRequestException(
          "Start date must be before or equal to end date: startDate="
              + startDate
              + ", endDate="
              + endDate);
    }
  }

  private void writeCsv(List<FundValue> values, OutputStream outputStream) {
    try {
      CSVFormat format =
          CSVFormat.DEFAULT.builder().setHeader("date", "nav").setDelimiter(',').get();

      try (CSVPrinter printer =
          new CSVPrinter(new OutputStreamWriter(outputStream, UTF_8), format)) {
        for (FundValue value : values) {
          printer.printRecord(value.date(), value.value());
        }
        printer.flush();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
