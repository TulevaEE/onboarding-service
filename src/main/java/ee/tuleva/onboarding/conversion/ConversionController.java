package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class ConversionController {

  private final UserConversionService userConversionService;

  @Operation(summary = "Get info about the current user conversion")
  @GetMapping("/me/conversion")
  public ConversionResponse conversion(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return userConversionService.getConversion(authenticatedPerson);
  }
}
