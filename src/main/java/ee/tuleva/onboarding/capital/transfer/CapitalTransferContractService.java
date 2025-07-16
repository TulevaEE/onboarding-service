package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContractContentService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CapitalTransferContractService {

  private final CapitalTransferContractRepository contractRepository;
  private final UserService userService;
  private final MemberService memberService;
  private final EmailService emailService;
  private final CapitalTransferFileService capitalTransferFileService;
  private final CapitalTransferContractContentService contractContentService;

  public CapitalTransferContract create(
      AuthenticatedPerson sellerPerson, CreateCapitalTransferContractCommand command) {
    User sellerUser = userService.getById(sellerPerson.getUserId()).orElseThrow();
    Member seller = sellerUser.getMemberOrThrow();
    Member buyer = memberService.getById(command.getBuyerMemberId());

    validate(seller, buyer, command);

    CapitalTransferContract contract =
        CapitalTransferContract.builder()
            .seller(seller)
            .buyer(buyer)
            .iban(command.getIban())
            .unitPrice(command.getUnitPrice())
            .unitCount(command.getUnitCount())
            .unitsOfMemberBonus(command.getUnitsOfMemberBonus())
            .state(CapitalTransferContractState.CREATED)
            .build();

    byte[] contractContent = contractContentService.generateContractContent(contract);
    contract.setOriginalContent(contractContent);

    return contractRepository.save(contract);
  }

  private void validate(Member seller, Member buyer, CreateCapitalTransferContractCommand command) {
    // TODO: Implement actual validation logic
    // 1. Check if seller has enough capital
    log.info(
        "Validating seller {} has {} units of member capital, {} of that member bonus",
        seller.getId(),
        command.getUnitCount(),
        command.getUnitsOfMemberBonus());
    // 2. Check for 10% concentration limit for buyer
    log.info("Validating concentration limit for buyer {}", buyer.getId());
    if (seller.getId().equals(buyer.getId())) {
      throw new IllegalArgumentException("Seller and buyer cannot be the same person.");
    }
  }

  public CapitalTransferContract getContract(Long id) {
    return contractRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Contract not found with id " + id));
  }

  public void signBySeller(Long contractId, byte[] container) {
    CapitalTransferContract contract = getContract(contractId);
    contract.signBySeller(container);
    contractRepository.save(contract);
    log.info("Contract {} signed by seller {}", contractId, contract.getSeller().getId());

    sendContractEmail(contract.getSeller().getUser(), CAPITAL_TRANSFER_SELLER_SIGNED, contract);
    sendContractEmail(contract.getBuyer().getUser(), CAPITAL_TRANSFER_BUYER_TO_SIGN, contract);
  }

  public void signByBuyer(Long contractId, byte[] container) {
    CapitalTransferContract contract = getContract(contractId);
    contract.signByBuyer(container);
    contractRepository.save(contract);
    log.info("Contract {} signed by buyer {}", contractId, contract.getBuyer().getId());
  }

  public CapitalTransferContract confirmPaymentByBuyer(Long id) {
    CapitalTransferContract contract = getContract(id);
    contract.confirmPaymentByBuyer();
    log.info("Payment confirmed by buyer for contract {}", id);
    sendContractEmail(
        contract.getSeller().getUser(), CAPITAL_TRANSFER_CONFIRMED_BY_BUYER, contract);
    return contractRepository.save(contract);
  }

  public CapitalTransferContract confirmPaymentBySeller(Long id) {
    CapitalTransferContract contract = getContract(id);
    contract.confirmPaymentBySeller();
    log.info("Payment confirmed by seller for contract {}.", id);
    return contractRepository.save(contract);
  }

  public List<SignatureFile> getSignatureFiles(Long contractId) {
    return capitalTransferFileService.getContractFiles(contractId);
  }

  private void sendContractEmail(
      User recipient, EmailType emailType, CapitalTransferContract contract) {
    if (recipient.getEmail() == null) {
      log.error("User {} has no email, not sending email {}", recipient.getId(), emailType);
      return;
    }

    Map<String, Object> mergeVars =
        Map.of(
            "firstName", recipient.getFirstName(),
            "sellerFirstName", contract.getSeller().getUser().getFirstName(),
            "sellerLastName", contract.getSeller().getUser().getLastName(),
            "buyerFirstName", contract.getBuyer().getUser().getFirstName(),
            "buyerLastName", contract.getBuyer().getUser().getLastName(),
            "contractId", contract.getId());

    var templateName = emailType.getTemplateName("et");
    MandrillMessage message =
        emailService.newMandrillMessage(
            // TODO language
            recipient.getEmail(), templateName, mergeVars, List.of("capital-transfer"), null);

    emailService.send(recipient, message, templateName);
  }
}
