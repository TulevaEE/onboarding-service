package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.*;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestFixture.redemptionRequestFixture;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.payment.EndToEndIdConverter;
import ee.tuleva.onboarding.banking.payment.PaymentRequest;
import ee.tuleva.onboarding.banking.payment.RequestPaymentEvent;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RedemptionPayoutServiceTest {

  private static final Instant NOW = Instant.parse("2025-01-15T15:00:00Z");
  private static final String CUSTOMER_IBAN = "EE123456789012345678";

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private RedemptionStatusService redemptionStatusService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BankAccounts bankAccounts;
  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private UserRepository userRepository;

  private RedemptionPayoutService service;

  @BeforeEach
  void setUp() {
    service =
        new RedemptionPayoutService(
            Clock.fixed(NOW, UTC),
            redemptionRequestRepository,
            redemptionStatusService,
            eventPublisher,
            bankAccounts,
            savingFundPaymentRepository,
            new EndToEndIdConverter(),
            companyRepository,
            userRepository);
  }

  @Test
  void payOutHeld_sendsThePaymentAndMarksRedeemed() {
    var user = sampleUser().build();
    var party = new PartyId(PERSON, user.getPersonalCode());
    var requestId = UUID.fromString("2db696b5-00ee-4937-87b4-8192c675e4b5");
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(PAYOUT_HELD)
            .customerIban(CUSTOMER_IBAN)
            .cashAmount(new BigDecimal("25.00"))
            .holdReason("PEP")
            .reviewedAt(NOW)
            .build();
    given(redemptionRequestRepository.findById(requestId)).willReturn(Optional.of(request));
    given(bankAccounts.getIban(TKF100, WITHDRAWAL_EUR)).willReturn("withdrawal-IBAN");
    given(savingFundPaymentRepository.findRemitterNameByIban(party, CUSTOMER_IBAN))
        .willReturn(Optional.of("John Smith"));

    service.payOutHeld(requestId);

    var expectedPayment =
        PaymentRequest.tulevaPaymentBuilder("2db696b500ee493787b48192c675e4b5")
            .remitterIban("withdrawal-IBAN")
            .beneficiaryName("John Smith")
            .beneficiaryIban(CUSTOMER_IBAN)
            .amount(new BigDecimal("25.00"))
            .description("Fondi tagasivõtmine")
            .build();
    verify(eventPublisher).publishEvent(new RequestPaymentEvent(expectedPayment, requestId));
    verify(redemptionStatusService).changeStatus(requestId, REDEEMED);
    assertThat(request.getProcessedAt()).isEqualTo(NOW);
  }

  @Test
  void payOutHeld_marksTheRequestFailedWhenTheBeneficiaryCannotBeResolved() {
    var user = sampleUser().build();
    var party = new PartyId(PERSON, user.getPersonalCode());
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .userId(user.getId())
            .status(PAYOUT_HELD)
            .customerIban(CUSTOMER_IBAN)
            .cashAmount(new BigDecimal("25.00"))
            .holdReason("PEP")
            .reviewedAt(NOW)
            .build();
    given(redemptionRequestRepository.findById(requestId)).willReturn(Optional.of(request));
    given(savingFundPaymentRepository.findRemitterNameByIban(party, CUSTOMER_IBAN))
        .willReturn(Optional.empty());
    given(userRepository.findByPersonalCode(user.getPersonalCode())).willReturn(Optional.empty());

    service.payOutHeld(requestId);

    verify(eventPublisher, never()).publishEvent(any(RequestPaymentEvent.class));
    verify(redemptionStatusService).changeStatus(requestId, FAILED);
    verify(redemptionStatusService, never()).changeStatus(requestId, REDEEMED);
    assertThat(request.getErrorReason()).contains("Beneficiary name not resolvable");
  }

  @Test
  void payOutHeld_rejectsRequestWhosePayoutIsNotHeld() {
    var requestId = UUID.randomUUID();
    var request =
        redemptionRequestFixture()
            .id(requestId)
            .status(VERIFIED)
            .cashAmount(new BigDecimal("25.00"))
            .build();
    given(redemptionRequestRepository.findById(requestId)).willReturn(Optional.of(request));

    assertThatThrownBy(() -> service.payOutHeld(requestId))
        .isInstanceOf(IllegalStateException.class);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void payOutHeld_throwsWhenRequestNotFound() {
    var requestId = UUID.randomUUID();
    given(redemptionRequestRepository.findById(requestId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.payOutHeld(requestId))
        .isInstanceOf(NoSuchElementException.class);
  }
}
