package ee.tuleva.onboarding.capital.transfer;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/capital-transfer-contracts")
@RequiredArgsConstructor
public class CapitalTransferContractController {

  private final CapitalTransferContractService contractService;
  private final CapitalTransferSignatureService signatureService;

  @Operation(summary = "Create a capital transfer contract")
  @PostMapping
  public CapitalTransferContractDto createContract(
      @AuthenticationPrincipal AuthenticatedPerson seller,
      @Valid @RequestBody CreateCapitalTransferContractCommand command) {
    CapitalTransferContract contract = contractService.create(seller, command);
    return CapitalTransferContractDto.from(contract);
  }

  @Operation(summary = "Get a capital transfer contract by ID")
  @GetMapping("/{id}")
  public CapitalTransferContractDto getContract(@PathVariable Long id) {
    return CapitalTransferContractDto.from(contractService.getContract(id));
  }

  @Operation(summary = "Update the state of a capital transfer contract")
  @PatchMapping("/{id}")
  public CapitalTransferContractDto updateContractState(
      @PathVariable Long id,
      @Valid @RequestBody UpdateCapitalTransferContractStateCommand command) {
    CapitalTransferContract contract;
    switch (command.getState()) {
      case PAYMENT_CONFIRMED_BY_BUYER -> contract = contractService.confirmPaymentByBuyer(id);
      case PAYMENT_CONFIRMED_BY_SELLER -> contract = contractService.confirmPaymentBySeller(id);
      case APPROVED -> contract = contractService.approve(id);
      default ->
          throw new IllegalArgumentException("Unsupported state transition: " + command.getState());
    }
    return CapitalTransferContractDto.from(contract);
  }

  @Operation(summary = "Start Smart-ID signing for a capital transfer contract")
  @PutMapping("/{id}/signature/smart-id")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.startSmartIdSignature(id, authenticatedPerson);
  }

  @Operation(summary = "Get Smart-ID signing status for a capital transfer contract")
  @GetMapping("/{id}/signature/smart-id/status")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.getSmartIdSignatureStatus(id, authenticatedPerson);
  }

  @Operation(summary = "Start ID Card signing for a capital transfer contract")
  @PutMapping("/{id}/signature/id-card")
  public IdCardSignatureResponse startIdCardSignature(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    return signatureService.startIdCardSignature(id, authenticatedPerson, signCommand);
  }

  @Operation(
      summary =
          "Persist ID Card signed hash and get signing status for a capital transfer contract")
  @PutMapping("/{id}/signature/id-card/status")
  public IdCardSignatureStatusResponse persistIdCardSignedHashOrGetSignatureStatus(
      @PathVariable Long id,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.persistIdCardSignedHashAndGetProcessingStatus(
        id, signCommand, authenticatedPerson);
  }

  @Operation(summary = "Start Mobile ID signing for a capital transfer contract")
  @PutMapping("/{id}/signature/mobile-id")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.startMobileIdSignature(id, authenticatedPerson);
  }

  @Operation(summary = "Get Mobile ID signing status for a capital transfer contract")
  @GetMapping("/{id}/signature/mobile-id/status")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.getMobileIdSignatureStatus(id, authenticatedPerson);
  }
}
