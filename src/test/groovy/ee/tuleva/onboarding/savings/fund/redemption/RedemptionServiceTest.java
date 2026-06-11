package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static ee.tuleva.onboarding.currency.Currency.EUR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.company.BoardMembershipService;
import ee.tuleva.onboarding.event.TrackableEvent;
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import ee.tuleva.onboarding.savings.fund.SavingFundDeadlinesService;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import ee.tuleva.onboarding.savings.fund.nav.FundNavProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RedemptionServiceTest {

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private LedgerService ledgerService;
  @Mock private SavingsFundLedger savingsFundLedger;
  @Mock private SavingsFundOnboardingService savingsFundOnboardingService;
  @Mock private BoardMembershipService boardMembershipService;
  @Mock private FundNavProvider navProvider;
  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private IbanWhitelistService ibanWhitelistService;
  @Mock private SavingFundDeadlinesService deadlinesService;
  @Mock private Clock clock;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks private RedemptionService redemptionService;

  private static final String PARENT_CODE = "38812121215";
  private static final String CHILD_CODE = "61506150006";
  private static final String IBAN = "EE471000001020145685";
  private static final BigDecimal AMOUNT = new BigDecimal("50.00");
  private static final UUID REQUEST_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Test
  void createRedemptionRequest_whileRepresentingChild_publishesMinorRedemptionEvent() {
    AuthenticatedPerson parentRepresentingChild = parentRepresentingChild();
    PartyId childParty = new PartyId(PartyId.Type.PERSON, CHILD_CODE);

    given(savingsFundOnboardingService.isOnboardingCompleted(childParty)).willReturn(true);
    given(savingFundPaymentRepository.findWithdrawableIbans(childParty)).willReturn(List.of(IBAN));
    given(navProvider.getDisplayNav(any())).willReturn(new BigDecimal("1.00"));
    LedgerAccount account =
        org.mockito.Mockito.mock(LedgerAccount.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    given(account.getBalance()).willReturn(new BigDecimal("-1000.00"));
    given(ledgerService.getPartyAccount(any(), any(), any())).willReturn(account);
    given(redemptionRequestRepository.save(any()))
        .willAnswer(
            invocation -> {
              RedemptionRequest req = invocation.getArgument(0);
              req.setId(REQUEST_ID);
              return req;
            });

    redemptionService.createRedemptionRequest(parentRepresentingChild, AMOUNT, EUR, IBAN);

    verify(applicationEventPublisher)
        .publishEvent(
            new TrackableEvent(
                parentRepresentingChild,
                TrackableEventType.MINOR_REDEMPTION,
                Map.of(
                    "childPersonalCode", CHILD_CODE,
                    "redemptionRequestId", REQUEST_ID,
                    "amount", AMOUNT)));
  }

  @Test
  void createRedemptionRequest_actingAsSelf_publishesNoMinorRedemptionEvent() {
    AuthenticatedPerson self = sampleAuthenticatedPersonAndMember().build();
    PartyId selfParty = self.toPartyId();

    given(savingsFundOnboardingService.isOnboardingCompleted(selfParty)).willReturn(true);
    given(savingFundPaymentRepository.findWithdrawableIbans(selfParty)).willReturn(List.of(IBAN));
    given(navProvider.getDisplayNav(any())).willReturn(new BigDecimal("1.00"));
    LedgerAccount account =
        org.mockito.Mockito.mock(LedgerAccount.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    given(account.getBalance()).willReturn(new BigDecimal("-1000.00"));
    given(ledgerService.getPartyAccount(any(), any(), any())).willReturn(account);
    given(redemptionRequestRepository.save(any()))
        .willAnswer(
            invocation -> {
              RedemptionRequest req = invocation.getArgument(0);
              req.setId(REQUEST_ID);
              return req;
            });

    redemptionService.createRedemptionRequest(self, AMOUNT, EUR, IBAN);

    verify(applicationEventPublisher, never()).publishEvent(any(TrackableEvent.class));
  }

  private AuthenticatedPerson parentRepresentingChild() {
    return AuthenticatedPerson.builder()
        .personalCode(PARENT_CODE)
        .firstName("PARENT")
        .lastName("PERSON")
        .userId(100L)
        .role(new Role(PERSON, CHILD_CODE, "Mari Maasikas"))
        .build();
  }
}
