package ee.tuleva.onboarding.insurance;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.SignatureController;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/insurance-transfer-forms")
@RequiredArgsConstructor
public class InsuranceTransferFormSigningController implements SignatureController<Long> {

  private final InsuranceTransferFormSignatureService signatureService;

  @Override
  @Operation(summary = "Start Smart-ID signing of an insurance transfer form")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.startSmartIdSignature(id, person);
  }

  @Override
  @Operation(summary = "Get the Smart-ID signing status of an insurance transfer form")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.getSmartIdSignatureStatus(id, person);
  }

  @Override
  @Operation(summary = "Start ID-card signing of an insurance transfer form")
  public IdCardSignatureResponse startIdCardSignature(
      @PathVariable("id") Long id,
      @AuthenticationPrincipal AuthenticatedPerson person,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    return signatureService.startIdCardSignature(id, person, signCommand);
  }

  @Override
  @Operation(summary = "Persist the ID-card signature of an insurance transfer form")
  public IdCardSignatureStatusResponse persistIdCardSignature(
      @PathVariable("id") Long id,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.persistIdCardSignature(id, signCommand, person);
  }

  @Override
  @Operation(summary = "Get the ID-card signing status of an insurance transfer form")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.getIdCardSignatureStatus(id, person);
  }

  @Override
  @Operation(summary = "Start Mobile-ID signing of an insurance transfer form")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.startMobileIdSignature(id, person);
  }

  @Override
  @Operation(summary = "Get the Mobile-ID signing status of an insurance transfer form")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long id, @AuthenticationPrincipal AuthenticatedPerson person) {
    return signatureService.getMobileIdSignatureStatus(id, person);
  }
}
