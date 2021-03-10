package ee.tuleva.onboarding.comparisons.returns;

import static org.springframework.format.annotation.DateTimeFormat.ISO.DATE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class ReturnsController {

  static final LocalDate BEGINNING_OF_TIMES = LocalDate.parse("2003-01-07");

  private final ReturnsService returnsService;

  @GetMapping("/returns")
  public Returns getReturns(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson person,
      @RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from,
      @RequestParam(required = false, name = "keys[]") List<String> keys) {
    LocalDate startDate = (from == null) ? BEGINNING_OF_TIMES : from;
    return returnsService.get(person, startDate, keys);
  }
}
