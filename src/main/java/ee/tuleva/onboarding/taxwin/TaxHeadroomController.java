package ee.tuleva.onboarding.taxwin;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/third-pillar/tax-headroom")
@RequiredArgsConstructor
class TaxHeadroomController {

  private final TaxHeadroomService headroomService;

  @Operation(summary = "Get how much more the person can pay into their III pillar with a tax win")
  @GetMapping
  ResponseEntity<TaxHeadroomResponse> getHeadroom(
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return headroomService
        .headroom(person)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(summary = "Set the income the person expects for a month that is not declared yet")
  @PutMapping("/expected-income/{month}")
  ResponseEntity<TaxHeadroomResponse> correctExpectedIncome(
      @PathVariable("month") YearMonth month,
      @Valid @RequestBody ExpectedIncomeCorrection correction,
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return ResponseEntity.of(
        headroomService.correctExpectedIncome(person, month, correction.amount()));
  }

  @Operation(
      summary =
          "Set the income the person expects for the whole year, spread over the months not declared yet")
  @PutMapping("/yearly-income")
  ResponseEntity<TaxHeadroomResponse> expectYearlyIncome(
      @Valid @RequestBody ExpectedYearlyIncome income,
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return ResponseEntity.of(headroomService.expectYearlyIncome(person, income.amount()));
  }

  @Operation(summary = "Go back to the estimated income for a month that is not declared yet")
  @DeleteMapping("/expected-income/{month}")
  ResponseEntity<TaxHeadroomResponse> removeCorrection(
      @PathVariable("month") YearMonth month, @AuthenticationPrincipal AuthenticatedPerson person) {
    return ResponseEntity.of(headroomService.removeCorrection(person, month));
  }
}
