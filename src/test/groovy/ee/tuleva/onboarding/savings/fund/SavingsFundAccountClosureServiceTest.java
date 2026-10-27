package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.cashAccountWithBalance;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.cashRedemptionAccountWithBalance;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.cashReservedAccountWithBalance;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.fundUnitsAccountWithBalance;
import static ee.tuleva.onboarding.ledger.LedgerAccountFixture.fundUnitsReservedAccountWithBalance;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.CLOSED;
import static ee.tuleva.onboarding.savings.SavingsFundOnboardingStatus.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerParty;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.UserAccount;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.PendingRedemption;
import ee.tuleva.onboarding.savings.RedemptionQueries;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.SavingFundPaymentQueries;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavingsFundAccountClosureServiceTest {

  @Mock private SavingsFundOnboardingRepository onboardingRepository;
  @Mock private LedgerService ledgerService;
  @Mock private SavingFundPaymentQueries paymentQueries;
  @Mock private RedemptionQueries redemptionQueries;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private SavingsFundAccountClosureService closureService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonNonMember().build();
  private final PartyId partyId = new PartyId(PartyId.Type.PERSON, person.getPersonalCode());

  @BeforeEach
  void openAccountWithNothingInIt() {
    given(onboardingRepository.isOnboardingCompleted(partyId.code(), PartyId.Type.PERSON))
        .willReturn(true);
    given(paymentQueries.getPendingPayments(partyId)).willReturn(List.of());
    given(redemptionQueries.getPendingRedemptions(partyId)).willReturn(List.of());
  }

  @Test
  void isClosable_whenOwnOpenAccountIsEmptyAndNothingIsPending() {
    assertThat(closureService.isClosable(person)).isTrue();
  }

  @Test
  void isClosable_whenLedgerAccountsExistWithZeroBalances() {
    holderAccounts()
        .forEach(
            account -> givenLedgerAccount(account, accountWithBalance(account, BigDecimal.ZERO)));

    assertThat(closureService.isClosable(person)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserAccount.class,
      names = {"CASH", "CASH_RESERVED", "CASH_REDEMPTION", "FUND_UNITS", "FUND_UNITS_RESERVED"})
  void isClosable_isFalseWhileTheAccountHoldsMoneyOrUnits(UserAccount account) {
    givenLedgerAccount(account, accountWithBalance(account, BigDecimal.ONE));

    assertThat(closureService.isClosable(person)).isFalse();
  }

  @Test
  void isClosable_isFalseWhenAccountIsNotOpen() {
    given(onboardingRepository.isOnboardingCompleted(partyId.code(), PartyId.Type.PERSON))
        .willReturn(false);

    assertThat(closureService.isClosable(person)).isFalse();
  }

  @Test
  void isClosable_isFalseWhenPaymentIsPending() {
    given(paymentQueries.getPendingPayments(partyId))
        .willReturn(List.of(SavingFundPayment.builder().build()));

    assertThat(closureService.isClosable(person)).isFalse();
  }

  @Test
  void isClosable_isFalseWhenRedemptionIsPending() {
    given(redemptionQueries.getPendingRedemptions(partyId))
        .willReturn(List.of(PendingRedemption.builder().build()));

    assertThat(closureService.isClosable(person)).isFalse();
  }

  @Test
  void isClosable_isFalseWhenActingForCompany() {
    var actingForCompany = sampleAuthenticatedPersonLegalEntity().build();

    assertThat(closureService.isClosable(actingForCompany)).isFalse();
  }

  @Test
  void isClosable_isFalseWhenActingForChild() {
    var actingForChild = sampleAuthenticatedPersonNonMember().role(childRole()).build();

    assertThat(closureService.isClosable(actingForChild)).isFalse();
  }

  @Test
  void close_marksAccountClosedAndTracksTheStatusChange() {
    closureService.close(person);

    var inOrder = inOrder(onboardingRepository);
    inOrder.verify(onboardingRepository).lockOnboarding(partyId.code());
    inOrder.verify(onboardingRepository).isOnboardingCompleted(partyId.code(), PartyId.Type.PERSON);

    verify(onboardingRepository).saveOnboardingStatus(partyId.code(), PartyId.Type.PERSON, CLOSED);
    verify(eventPublisher)
        .publishEvent(
            new TrackableEvent(
                person,
                SAVINGS_FUND_ONBOARDING_STATUS_CHANGE,
                Map.of("oldStatus", COMPLETED, "newStatus", CLOSED)));
  }

  @Test
  void close_refusesWhenAccountIsNotClosable() {
    given(paymentQueries.getPendingPayments(partyId))
        .willReturn(List.of(SavingFundPayment.builder().build()));

    assertThatThrownBy(() -> closureService.close(person))
        .isInstanceOf(SavingsFundAccountClosureRefusedException.class);
    verify(onboardingRepository, never()).saveOnboardingStatus(any(), any(), any());
    verifyNoInteractions(eventPublisher);
  }

  private static List<UserAccount> holderAccounts() {
    return List.of(
        UserAccount.CASH,
        UserAccount.CASH_RESERVED,
        UserAccount.CASH_REDEMPTION,
        UserAccount.FUND_UNITS,
        UserAccount.FUND_UNITS_RESERVED);
  }

  private void givenLedgerAccount(UserAccount account, LedgerAccount ledgerAccount) {
    given(ledgerService.findPartyAccount(partyId.code(), LedgerParty.PartyType.PERSON, account))
        .willReturn(Optional.of(ledgerAccount));
  }

  private static LedgerAccount accountWithBalance(UserAccount account, BigDecimal balance) {
    return switch (account) {
      case CASH -> cashAccountWithBalance(balance);
      case CASH_RESERVED -> cashReservedAccountWithBalance(balance);
      case CASH_REDEMPTION -> cashRedemptionAccountWithBalance(balance);
      case FUND_UNITS -> fundUnitsAccountWithBalance(balance);
      case FUND_UNITS_RESERVED -> fundUnitsReservedAccountWithBalance(balance);
      default -> throw new IllegalArgumentException("Not a holder account: " + account);
    };
  }

  private static Role childRole() {
    return new Role(PERSON, "61506150006", "Test Child");
  }
}
