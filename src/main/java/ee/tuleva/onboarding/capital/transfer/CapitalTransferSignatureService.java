package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatus;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalTransferSignatureService {
  private final CapitalTransferContractService contractService;
  private final SignatureService signService;
  private final GenericSessionStore sessionStore;
  private final UserService userService;

  public MobileSignatureResponse startSmartIdSignature(
      Long contractId, AuthenticatedPerson authenticatedPerson) {
    User user = userService.getById(authenticatedPerson.getUserId());
    List<SignatureFile> files = contractService.getSignatureFiles(contractId);

    SmartIdSignatureSession signatureSession =
        signService.startSmartIdSign(files, user.getPersonalCode());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      Long contractId, AuthenticatedPerson authenticatedPerson) {
    SmartIdSignatureSession session =
        sessionStore
            .get(SmartIdSignatureSession.class)
            .orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    User user = userService.getById(authenticatedPerson.getUserId());
    CapitalTransferContract contract = contractService.getContract(contractId);

    byte[] signedFile = signService.getSignedFile(session);

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new MobileSignatureStatusResponse(
          MandateSignatureStatus.SIGNATURE, session.getVerificationCode());
    }

    return new MobileSignatureStatusResponse(
        MandateSignatureStatus.OUTSTANDING_TRANSACTION, session.getVerificationCode());
  }

  private void finalizeSignature(CapitalTransferContract contract, User user, byte[] signedFile) {
    if (contract.getState() == CapitalTransferContractState.CREATED
        && contract.getSeller().getUser().equals(user)) {
      contractService.signBySeller(contract.getId(), signedFile);
    } else if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getBuyer().getUser().equals(user)) {
      contractService.signByBuyer(contract.getId(), signedFile);
    } else {
      log.error(
          "Cannot sign contract {} in state {} by user {}",
          contract.getId(),
          contract.getState(),
          user.getId());
      throw new IllegalStateException("Cannot sign contract in its current state");
    }
  }
}
