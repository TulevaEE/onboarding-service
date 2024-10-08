package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchController.MANDATE_BATCHES_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_BATCHES_URI)
@RequiredArgsConstructor
public class MandateBatchController {
  public static final String MANDATE_BATCHES_URI = "/mandate-batches";

  private final MandateBatchService mandateBatchService;
  private final MandateBatchSignatureService mandateBatchSignatureService;

  @Operation(summary = "Create mandate batch")
  @PostMapping()
  public MandateBatchDto createMandateBatch(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody MandateBatchDto mandateBatchDto) {

    return MandateBatchDto.from(
        mandateBatchService.createMandateBatch(authenticatedPerson, mandateBatchDto));
  }

  @Operation(summary = "Start signing mandate batch with Smart ID")
  @PutMapping("/{id}/signature/smart-id")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.startSmartIdSignature(mandateBatchId, authenticatedPerson);
  }

  @Operation(summary = "Is mandate batch successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smart-id/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.getSmartIdSignatureStatus(
        mandateBatchId, authenticatedPerson);
  }

  @Operation(summary = "Start signing mandate batch with ID card")
  @PutMapping("/{id}/signature/id-card")
  public IdCardSignatureResponse startIdCardSign(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    return mandateBatchSignatureService.startIdCardSign(
        mandateBatchId, authenticatedPerson, signCommand);
  }

  @Operation(summary = "Is mandate batch successfully signed with ID card")
  @PutMapping("/{id}/signature/id-card/status")
  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.getIdCardSignatureStatus(
        mandateBatchId, signCommand, authenticatedPerson);
  }

  @Operation(summary = "Start signing mandate batch with mobile ID")
  @PutMapping("/{id}/signature/mobile-id")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.startMobileIdSignature(mandateBatchId, authenticatedPerson);
  }

  @Operation(summary = "Is mandate batch successfully signed with mobile ID")
  @GetMapping("/{id}/signature/mobile-id/status")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.getMobileIdSignatureStatus(
        mandateBatchId, authenticatedPerson);
  }
}
