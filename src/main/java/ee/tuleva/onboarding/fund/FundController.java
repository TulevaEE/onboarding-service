package ee.tuleva.onboarding.fund;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValueCsvExporter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundController {

  private final FundService fundService;
  private final FundValueCsvExporter csvExporter;

  @Operation(summary = "Get info about available funds")
  @GetMapping("/funds")
  public List<ExtendedApiFundResponse> get(
      @RequestParam("fundManager.name") Optional<String> fundManagerName) {
    return fundService.getFunds(fundManagerName);
  }

  @Operation(summary = "Export fund NAV history as CSV")
  @GetMapping(value = "/funds/{isin}/nav")
  public void exportFundNav(
      @PathVariable @NotBlank String isin,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate endDate,
      HttpServletResponse response)
      throws IOException {

    LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.of(2001, 1, 1);
    LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

    String filename =
        String.format("fund-nav-%s-%s-%s.csv", isin, effectiveStartDate, effectiveEndDate);

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      csvExporter.exportToCsv(isin, effectiveStartDate, effectiveEndDate, buffer);
    } catch (NotFoundException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    } catch (BadRequestException e) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }

    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
    response.getOutputStream().write(buffer.toByteArray());
    response.flushBuffer();
  }
}
