package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH_REDEMPTION;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH_RESERVED;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.CLOSED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.RedemptionQueries;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class SavingsFundAccountClosureService {

  private static final List<UserAccount> ACCOUNTS_HOLDING_THE_HOLDERS_MONEY_OR_UNITS =
      List.of(CASH, CASH_RESERVED, CASH_REDEMPTION, FUND_UNITS, FUND_UNITS_RESERVED);

  private final SavingsFundOnboardingRepository onboardingRepository;
  private final LedgerService ledgerService;
  private final SavingFundPaymentQueries paymentQueries;
  private final RedemptionQueries redemptionQueries;
  private final ApplicationEventPublisher eventPublisher;

  public boolean isClosable(AuthenticatedPerson person) {
    if (!person.isActingAsSelf()) {
      return false;
    }
    var partyId = ownAccount(person);
    return onboardingRepository.isOnboardingCompleted(partyId.code(), PERSON)
        && holdsNoMoneyOrUnits(partyId)
        && paymentQueries.getPendingPayments(partyId).isEmpty()
        && redemptionQueries.getPendingRedemptions(partyId).isEmpty();
  }

  @Transactional
  public void close(AuthenticatedPerson person) {
    onboardingRepository.lockOnboarding(person.getPersonalCode());
    if (!isClosable(person)) {
      throw new SavingsFundAccountClosureRefusedException(
          "Savings fund account cannot be closed: userId=" + person.getUserId());
    }
    onboardingRepository.saveOnboardingStatus(person.getPersonalCode(), PERSON, CLOSED);
    log.info("Savings fund account closed: userId={}", person.getUserId());
    eventPublisher.publishEvent(
        new TrackableEvent(
            person,
            SAVINGS_FUND_ONBOARDING_STATUS_CHANGE,
            Map.of("oldStatus", COMPLETED, "newStatus", CLOSED)));
  }

  private static PartyId ownAccount(AuthenticatedPerson person) {
    return new PartyId(PERSON, person.getPersonalCode());
  }

  private boolean holdsNoMoneyOrUnits(PartyId partyId) {
    return ACCOUNTS_HOLDING_THE_HOLDERS_MONEY_OR_UNITS.stream()
        .map(
            account ->
                ledgerService.findPartyAccount(
                    partyId.code(), LedgerParty.PartyType.PERSON, account))
        .flatMap(Optional::stream)
        .map(LedgerAccount::getBalance)
        .allMatch(balance -> balance.signum() == 0);
  }
}
