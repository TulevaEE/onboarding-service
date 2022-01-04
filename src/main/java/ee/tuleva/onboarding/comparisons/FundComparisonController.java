package ee.tuleva.onboarding.comparisons;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import javax.validation.constraints.Past;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class FundComparisonController {

  private static final Instant BEGINNING_OF_TIME = parseInstant("2000-01-01");

  private final FundComparisonCalculatorService fundComparisonCalculatorService;

  private static Instant parseInstant(String format) {
    try {
      return new SimpleDateFormat("yyyy-MM-dd").parse(format).toInstant();
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  @Operation(summary = "Compare the current user's return to estonian and world average")
  @GetMapping("/fund-comparison")
  public FundComparison getComparison(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @RequestParam(value = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") @Past
          Date startDate,
      @RequestParam(value = "pillar", required = false, defaultValue = "2") Integer pillar) {
    Instant startTime = startDate == null ? BEGINNING_OF_TIME : startDate.toInstant();
    if (startTime.isBefore(BEGINNING_OF_TIME)) {
      throw new IllegalArgumentException("From date is too much in the past: " + startDate);
    }
    return fundComparisonCalculatorService.calculateComparison(
        authenticatedPerson, startTime, pillar);
  }
}
