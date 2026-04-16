package ee.tuleva.onboarding.fund;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

  private final FundService fundService;

  @Operation(summary = "Get info about available funds")
  @GetMapping("/funds")
  public List<ExtendedApiFundResponse> get(
      @RequestParam("fundManager.name") Optional<String> fundManagerName) {
    return fundService.getFunds(fundManagerName);
  }

  private static final DateTimeFormatter ESTONIAN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  @Operation(summary = "Get NAV history for a fund")
  @GetMapping("/funds/{isin}/nav")
  public Object getNavHistory(
      @PathVariable String isin,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) String format,
      HttpServletResponse response)
      throws IOException {
    List<NavValueResponse> navHistory = fundService.getNavHistory(isin, startDate, endDate);
    if ("csv".equals(format)) {
      writeCsvResponse(response, isin, navHistory);
      return null;
    }
    return navHistory;
  }

  private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private void writeCsvResponse(
      HttpServletResponse response, String isin, List<NavValueResponse> navHistory)
      throws IOException {
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"nav-" + isin + ".csv\"");
    var outputStream = response.getOutputStream();
    outputStream.write(UTF8_BOM);
    var format =
        CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader("Kuupäev", "NAV (EUR)").get();
    try (var printer = new CSVPrinter(new OutputStreamWriter(outputStream, UTF_8), format)) {
      for (var row : navHistory) {
        printer.printRecord(ESTONIAN_DATE.format(row.date()), row.value().toPlainString());
      }
    }
  }
}
