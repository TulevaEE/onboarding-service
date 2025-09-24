package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.*;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.event.TrackableEventType.CAPITAL_TRANSFER_STATE_CHANGE;
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getAttachments;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.*;
import static ee.tuleva.onboarding.notification.slack.SlackService.SlackChannel.CAPITAL_TRANSFER;
import static java.util.stream.Stream.concat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.CapitalRow;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContractContentService;
import ee.tuleva.onboarding.epis.contact.ContactDetailsService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.listing.MessageResponse;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.slack.SlackService;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final EmailPersistenceService emailPersistenceService;
  private final CapitalTransferFileService capitalTransferFileService;
  private final CapitalTransferContractContentService contractContentService;
  private final CapitalService capitalService;
  private final SlackService slackService;
  private final ContactDetailsService contactDetailsService;
  private final ApplicationEventPublisher eventPublisher;

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
            .transferAmounts(command.getTransferAmounts())
            .state(CapitalTransferContractState.CREATED)
            .build();

    byte[] contractContent = contractContentService.generateContractContent(contract);
    contract.setOriginalContent(contractContent);

    return contractRepository.save(contract);
  }

  private void validate(Member seller, Member buyer, CreateCapitalTransferContractCommand command) {
    log.info(
        "Validating command {} for seller {} and buyer {}", command, seller.getId(), buyer.getId());

    if (isAmountsEmpty(command)) {
      throw new IllegalArgumentException("No amounts specified");
    }

    if (!hasPositiveNonZeroAmountsPrices(command)) {
      throw new IllegalArgumentException("Amounts or prices have negative or zero values");
    }

    if (!hasOnlyOneOfType(command)) {
      throw new IllegalArgumentException("Duplicate types specified");
    }

    if (!hasOnlyLiquidatableTypes(command)) {
      throw new IllegalArgumentException("Non-liquidatable capital types included in command");
    }

    if (seller.getId().equals(buyer.getId())) {
      throw new IllegalArgumentException("Seller and buyer cannot be the same person.");
    }

    if (!hasEnoughMemberCapital(seller, command)) {
      throw new IllegalStateException("Seller does not have enough member capital");
    }

    if (!isTransferWithinConcentrationLimit(buyer, command)) {
      throw new IllegalStateException("Buyer would exceed concentration limit after transfer");
    }
  }

  private boolean isTransferWithinConcentrationLimit(
      Member buyer, CreateCapitalTransferContractCommand command) {
    var user = buyer.getUser();

    var totalMemberCapital =
        capitalService.getCapitalRows(user.getMemberId()).stream()
            .filter(event -> event.type() != UNVESTED_WORK_COMPENSATION)
            .map(CapitalRow::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var memberCapitalBookValueBeingAcquired =
        getCapitalBeingAcquiredInOtherTransfers(buyer).values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var memberCapitalBookValueToBeAcquired =
        command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::bookValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    var buyerMemberCapitalAfterPurchases =
        totalMemberCapital
            .add(memberCapitalBookValueBeingAcquired)
            .add(memberCapitalBookValueToBeAcquired);

    var concentrationLimit = capitalService.getCapitalConcentrationUnitLimit();
    return concentrationLimit.compareTo(buyerMemberCapitalAfterPurchases) > 0;
  }

  public Map<MemberCapitalEventType, BigDecimal> getCapitalBeingAcquiredInOtherTransfers(
      Member buyer) {
    var userPurchaseTransfers = contractRepository.findAllByBuyerId(buyer.getId());

    return getCapitalSumsOfActiveTransfers(userPurchaseTransfers);
  }

  public Map<MemberCapitalEventType, BigDecimal> getCapitalBeingSoldInOtherTransfers(
      Member seller) {
    var userSaleTransfers = contractRepository.findAllBySellerId(seller.getId());

    return getCapitalSumsOfActiveTransfers(userSaleTransfers);
  }

  private Map<MemberCapitalEventType, BigDecimal> getCapitalSumsOfActiveTransfers(
      List<CapitalTransferContract> userTransfers) {
    var activeTransfers =
        userTransfers.stream().filter(contract -> contract.getState().isInProgress());

    var allTransferAmounts =
        activeTransfers.flatMap(contract -> contract.getTransferAmounts().stream()).toList();

    return allTransferAmounts.stream()
        .collect(
            Collectors.toMap(
                CapitalTransferAmount::type, CapitalTransferAmount::bookValue, BigDecimal::add));
  }

  private boolean hasEnoughMemberCapital(
      Member seller, CreateCapitalTransferContractCommand command) {

    var capitalSoldInOtherTransfers = getCapitalBeingSoldInOtherTransfers(seller);

    return command.getTransferAmounts().stream()
        .allMatch(
            transferAmount -> {
              var totalSellerMemberCapitalOfType =
                  capitalService.getCapitalRows(seller.getId()).stream()
                      .filter(event -> event.type() == transferAmount.type())
                      .map(CapitalRow::getValue)
                      .reduce(BigDecimal.ZERO, BigDecimal::add);

              var capitalOfTypeBeingSoldInOtherTransfers =
                  capitalSoldInOtherTransfers.getOrDefault(transferAmount.type(), BigDecimal.ZERO);
              var totalCapitalToBeSold =
                  transferAmount.bookValue().add(capitalOfTypeBeingSoldInOtherTransfers);

              return totalSellerMemberCapitalOfType.compareTo(totalCapitalToBeSold) >= 0;
            });
  }

  private boolean isAmountsEmpty(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().isEmpty();
  }

  private boolean hasOnlyOneOfType(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::type)
            .collect(Collectors.toSet())
            .size()
        == command.getTransferAmounts().size();
  }

  private boolean hasPositiveNonZeroAmountsPrices(CreateCapitalTransferContractCommand command) {
    return command.getTransferAmounts().stream()
        .allMatch(
            amount ->
                amount.bookValue().compareTo(BigDecimal.ZERO) > 0
                    && amount.price().compareTo(BigDecimal.ZERO) > 0);
  }

  private boolean hasOnlyLiquidatableTypes(CreateCapitalTransferContractCommand command) {
    var typesToLiquidate =
        command.getTransferAmounts().stream()
            .map(CapitalTransferAmount::type)
            .collect(Collectors.toSet());
    var liquidatableTypes =
        Set.of(CAPITAL_PAYMENT, WORK_COMPENSATION, MEMBERSHIP_BONUS, CAPITAL_ACQUIRED);

    return liquidatableTypes.containsAll(typesToLiquidate);
  }

  public CapitalTransferContract getContract(Long id, User user) {
    var contract =
        contractRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Contract not found with id " + id));

    if (!contract.canBeAccessedBy(user)) {
      throw new IllegalArgumentException("Contract not found with id " + id);
    }

    return contract;
  }

  public List<CapitalTransferContract> getMyContracts(User user) {
    var myMemberId = user.getMemberId();

    var myBuyerContracts = contractRepository.findAllByBuyerId(myMemberId);
    var mySellerContracts = contractRepository.findAllBySellerId(myMemberId);

    return concat(myBuyerContracts.stream(), mySellerContracts.stream()).toList();
  }

  public void signBySeller(Long contractId, byte[] container, User user) {
    CapitalTransferContract contract = getContract(contractId, user);
    if (!contract.getSeller().getId().equals(user.getMemberId())) {
      throw new IllegalStateException("Can only be signed by seller at this point");
    }
    broadcastStateChangeEvent(() -> contract.signBySeller(container), contract, user);
    contractRepository.save(contract);
    log.info("Contract {} signed by seller {}", contractId, contract.getSeller().getId());

    sendContractEmail(contract.getBuyer().getUser(), CAPITAL_TRANSFER_BUYER_TO_SIGN, contract);
  }

  public void signByBuyer(Long contractId, byte[] container, User user) {
    CapitalTransferContract contract = getContract(contractId, user);
    if (!contract.getBuyer().getId().equals(user.getMemberId())) {
      throw new IllegalStateException("Can only be signed by buyer at this point");
    }
    broadcastStateChangeEvent(() -> contract.signByBuyer(container), contract, user);
    contractRepository.save(contract);
    log.info("Contract {} signed by buyer {}", contractId, contract.getBuyer().getId());
  }

  public CapitalTransferContract updateStateByUser(
      Long id, CapitalTransferContractState desiredState, User user) {
    CapitalTransferContract contract = getContract(id, user);

    if (contract.getState().equals(BUYER_SIGNED)
        && desiredState.equals(PAYMENT_CONFIRMED_BY_BUYER)) {
      return confirmPaymentByBuyer(id, user);
    }

    if (contract.getState().equals(PAYMENT_CONFIRMED_BY_BUYER)
        && desiredState.equals(PAYMENT_CONFIRMED_BY_SELLER)) {
      return confirmPaymentBySeller(id, user);
    }

    throw new IllegalArgumentException(
        "Unsupported state transition for contract(id=" + id + ") to " + desiredState);
  }

  public CapitalTransferContract updateStateBySystem(
      Long id, CapitalTransferContractState desiredState) {
    CapitalTransferContract contract = contractRepository.findById(id).orElseThrow();

    if (contract.getState().equals(EXECUTED) && desiredState.equals(APPROVED_AND_NOTIFIED)) {
      contract.approvedAndNotified();
      return contractRepository.save(contract);
    }

    throw new IllegalArgumentException(
        "Unsupported state transition for contract(id=" + id + ") to " + desiredState);
  }

  private CapitalTransferContract confirmPaymentByBuyer(Long id, User user) {
    CapitalTransferContract contract = getContract(id, user);

    if (!contract.getBuyer().getId().equals(user.getMemberId())) {
      throw new IllegalStateException("Payment can only be confirmed by buyer");
    }

    broadcastStateChangeEvent(contract::confirmPaymentByBuyer, contract, user);
    log.info("Payment confirmed by buyer for contract {}", id);
    sendContractEmail(
        contract.getSeller().getUser(), CAPITAL_TRANSFER_CONFIRMED_BY_BUYER, contract);
    return contractRepository.save(contract);
  }

  private CapitalTransferContract confirmPaymentBySeller(Long id, User user) {
    CapitalTransferContract contract = getContract(id, user);

    if (!contract.getSeller().getId().equals(user.getMemberId())) {
      throw new IllegalStateException("Payment can only be confirmed by seller");
    }

    broadcastStateChangeEvent(contract::confirmPaymentBySeller, contract, user);
    log.info("Payment confirmed by seller for contract {}.", id);
    sendContractEmail(
        contract.getBuyer().getUser(), CAPITAL_TRANSFER_CONFIRMED_BY_SELLER, contract);

    try {
      slackService.sendMessage(
          "Capital transfer id=" + contract.getId() + " awaiting board confirmation",
          CAPITAL_TRANSFER);
    } catch (Exception e) {
      log.error("Failed to notify about capital transfer id=" + contract.getId(), e);
    }

    return contractRepository.save(contract);
  }

  public List<SignatureFile> getSignatureFiles(Long contractId, User user) {
    // prevent enumeration
    var contract = getContract(contractId, user);
    return capitalTransferFileService.getContractFiles(contract.getId());
  }

  private void broadcastStateChangeEvent(
      Runnable stateUpdater, CapitalTransferContract contract, User user) {
    var oldState = contract.getState();
    stateUpdater.run();
    var newState = contract.getState();

    eventPublisher.publishEvent(
        new TrackableEvent(
            user,
            CAPITAL_TRANSFER_STATE_CHANGE,
            Map.of("id", contract.getId(), "oldState", oldState, "newState", newState)));
  }

  void sendContractEmail(User recipient, EmailType emailType, CapitalTransferContract contract) {
    if (recipient.getEmail() == null) {
      log.error("User {} has no email, not sending email {}", recipient.getId(), emailType);
      return;
    }

    Map<String, Object> mergeVars =
        Map.of(
            "fname", recipient.getFirstName(),
            "lname", recipient.getLastName(),
            "sellerFirstName", contract.getSellerFirstName(),
            "sellerLastName", contract.getSellerLastName(),
            "sellerFullName", contract.getSellerFullName(),
            "buyerFirstName", contract.getBuyerFirstName(),
            "buyerLastName", contract.getBuyerLastName(),
            "buyerFullName", contract.getBuyerFullName(),
            "contractId", contract.getId());

    var templateName = emailType.getTemplateName(getLanguage(recipient));
    var attachments =
        Set.of(CAPITAL_TRANSFER_CONFIRMED_BY_BUYER, CAPITAL_TRANSFER_CONFIRMED_BY_SELLER)
                .contains(emailType)
            ? getAttachments(contract)
            : null;

    MandrillMessage message =
        emailService.newMandrillMessage(
            recipient.getEmail(),
            templateName,
            mergeVars,
            List.of("capital-transfer"),
            attachments);

    var messageResponse =
        emailService
            .send(recipient, message, templateName)
            .map(
                response -> {
                  Email saved =
                      emailPersistenceService.save(
                          recipient, response.getId(), emailType, response.getStatus());
                  return new MessageResponse(saved.getId(), response.getStatus());
                });
  }

  private String getLanguage(User user) {
    // var contactDetails = contactDetailsService.getContactDetails(user);
    // return contactDetails.getLanguagePreference() == ENG ? "en" : "et";

    // hotfix: there is no authentication context when sending the email after board approval
    return "et";
  }
}
