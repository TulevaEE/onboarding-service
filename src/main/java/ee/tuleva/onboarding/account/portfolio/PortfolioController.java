package ee.tuleva.onboarding.account.portfolio;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PortfolioController {

  private static final LocalDate BEGINNING_OF_TIMES = LocalDate.parse("2003-01-07");

  private final PortfolioService portfolioService;
  private final Clock clock;

  @Operation(summary = "Get the value of a person's holdings over a period")
  @GetMapping("/portfolio")
  public Portfolio getPortfolio(
      @AuthenticationPrincipal AuthenticatedPerson person,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) @Nullable LocalDate to) {
    return portfolioService.getPortfolio(
        person, from == null ? BEGINNING_OF_TIMES : from, to == null ? LocalDate.now(clock) : to);
  }
}
