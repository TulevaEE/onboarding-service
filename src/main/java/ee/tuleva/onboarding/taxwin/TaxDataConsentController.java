package ee.tuleva.onboarding.taxwin;

import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tax-data-consents")
@RequiredArgsConstructor
class TaxDataConsentController {

  private final TaxDataConsentService consentService;
  private final LocaleService localeService;

  @Operation(summary = "Ask the consent service for a tax data consent for the person to sign")
  @PostMapping
  TaxDataConsentResponse create(@AuthenticationPrincipal AuthenticatedPerson person) {
    return consentService.create(person, localeService.getCurrentLocale());
  }

  @Operation(summary = "Get the tax data consent document the person is asked to sign")
  @GetMapping(value = "/{id}/document", produces = APPLICATION_PDF_VALUE)
  byte[] getDocument(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return consentService.document(id, person);
  }
}
