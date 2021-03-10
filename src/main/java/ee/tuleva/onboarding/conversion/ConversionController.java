package ee.tuleva.onboarding.conversion;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class ConversionController {

  private final UserConversionService userConversionService;

  @ApiOperation(value = "Get info about the current user conversion")
  @GetMapping("/me/conversion")
  public ConversionResponse conversion(
      @ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return userConversionService.getConversion(authenticatedPerson);
  }
}
