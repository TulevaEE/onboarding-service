package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.*;
import static ee.tuleva.onboarding.event.TrackableEventType.CAPITAL_TRANSFER_STATE_CHANGE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.CAPITAL_TRANSFER;
import static ee.tuleva.onboarding.notification.email.EmailType.*;
import static java.util.stream.Stream.concat;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEvent;
import ee.tuleva.onboarding.capital.event.AggregatedCapitalEventRepository;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.capital.transfer.content.CapitalTransferContractContentService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
  private final CapitalTransferFileService capitalTransferFileService;
  private final CapitalTransferContractContentService contractContentService;
  private final OperationsNotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;
  private final AggregatedCapitalEventRepository aggregatedCapitalEventRepository;
  private final CapitalTransferCreationValidator validator;
  private final CapitalTransferEmailSender emailSender;

  public CapitalTransferContract create(
      AuthenticatedPerson sellerPerson, CreateCapitalTransferContractCommand command) {
    User sellerUser = userService.getById(sellerPerson.getUserIdOrThrow()).orElseThrow();
    Member seller = sellerUser.getMemberOrThrow();
    Member buyer = memberService.getById(command.getBuyerMemberId());

    validator.validate(seller, buyer, command);

    BigDecimal currentOwnershipUnitPrice = getCurrentOwnershipUnitPrice();
    List<CapitalTransferAmount> transferAmountsWithUnitPrice =
        command.getTransferAmounts().stream()
            .map(
                amount ->
                    new CapitalTransferAmount(
                        amount.type(),
                        amount.price(),
                        amount.bookValue(),
                        currentOwnershipUnitPrice))
            .toList();

    CapitalTransferContract contract =
        CapitalTransferContract.builder()
            .seller(seller)
            .buyer(buyer)
            .iban(command.getIban())
            .transferAmounts(transferAmountsWithUnitPrice)
            .state(CapitalTransferContractState.CREATED)
            .build();

    byte[] contractContent = contractContentService.generateContractContent(contract);
    contract.setOriginalContent(contractContent);

    return contractRepository.save(contract);
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

    emailSender.sendContractEmail(
        contract.getBuyer().getUser(), CAPITAL_TRANSFER_BUYER_TO_SIGN, contract);
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
    emailSender.sendContractEmail(
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
    emailSender.sendContractEmail(
        contract.getBuyer().getUser(), CAPITAL_TRANSFER_CONFIRMED_BY_SELLER, contract);

    try {
      notificationService.sendMessage(
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

  private BigDecimal getCurrentOwnershipUnitPrice() {
    AggregatedCapitalEvent latestEvent =
        aggregatedCapitalEventRepository.findTopByOrderByDateDesc();

    if (latestEvent == null || latestEvent.getOwnershipUnitPrice() == null) {
      throw new IllegalStateException("Could not determine current ownership unit price");
    }

    return latestEvent.getOwnershipUnitPrice();
  }
}
