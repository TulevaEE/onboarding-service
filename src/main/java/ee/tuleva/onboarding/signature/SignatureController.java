package ee.tuleva.onboarding.signature;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// useSigning in frontend
@RequestMapping("/default")
public interface SignatureController<TEntityId> {
  @Operation(summary = "Start signing entity with Smart ID")
  @PutMapping("/{id}/signature/smart-id")
  MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Is entity successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smart-id/status")
  MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Start signing entity with ID card")
  @PutMapping("/{id}/signature/id-card")
  IdCardSignatureResponse startIdCardSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand);

  // TODO: split this into PUT and GET endpoints
  // Currently first call persists signed hex, and later polling calls just check if mandates have
  // been processsed
  @Operation(summary = "Persist ID-card signed entity, and check if entity is processed")
  @PutMapping("/{id}/signature/id-card/status")
  IdCardSignatureStatusResponse persistIdCardSignedHashOrGetSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Start signing entity with mobile ID")
  @PutMapping("/{id}/signature/mobile-id")
  MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Is entity successfully signed with mobile ID")
  @GetMapping("/{id}/signature/mobile-id/status")
  MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);
}
