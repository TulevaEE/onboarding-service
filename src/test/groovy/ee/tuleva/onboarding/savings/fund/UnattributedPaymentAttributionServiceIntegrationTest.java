package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.PERSON;
import static ee.tuleva.onboarding.ledger.SystemAccount.INCOMING_PAYMENTS_CLEARING;
import static ee.tuleva.onboarding.ledger.SystemAccount.UNRECONCILED_BANK_RECEIPTS;
import static ee.tuleva.onboarding.ledger.UserAccount.CASH;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.COMPLETED;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.tuleva.onboarding.ledger.LedgerAccount;
import ee.tuleva.onboarding.ledger.LedgerService;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UnattributedPaymentAttributionServiceIntegrationTest {

  @Autowired UnattributedPaymentAttributionService attributionService;
  @Autowired SavingFundPaymentRepository paymentRepository;
  @Autowired SavingsFundLedger savingsFundLedger;
  @Autowired LedgerService ledgerService;
  @Autowired UserRepository userRepository;
  @Autowired SavingsFundOnboardingRepository onboardingRepository;

  private static final String PERSONAL_CODE = "48806046007";
  private static final BigDecimal AMOUNT = new BigDecimal("1000.00");

  @Test
  void attribute_clearsParkingCreditsPartyAndVerifiesPayment() {
    var party = onboardedParty();
    var paymentId = createReturnedUnattributedPayment();

    var result = attributionService.attribute(paymentId, party, true);

    assertThat(result.getStatus()).isEqualTo(VERIFIED);
    assertThat(result.getPartyId()).isEqualTo(party);

    assertThat(unreconciledAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(incomingPaymentsAccount().getBalance()).isEqualByComparingTo(AMOUNT);
    assertThat(userCashAccount().getBalance()).isEqualByComparingTo(AMOUNT.negate());
  }

  @Test
  void attribute_rejectsPartyWithoutCompletedOnboarding() {
    var party = new PartyId(PartyId.Type.PERSON, PERSONAL_CODE);
    var paymentId = createReturnedUnattributedPayment();

    assertThatThrownBy(() -> attributionService.attribute(paymentId, party, true))
        .isInstanceOf(IllegalStateException.class);

    assertThat(unreconciledAccount().getBalance()).isEqualByComparingTo(AMOUNT.negate());
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(RETURNED);
  }

  @Test
  void attribute_rejectsAlreadyBouncedBackPayment() {
    var party = onboardedParty();
    var paymentId = createReturnedUnattributedPayment();
    savingsFundLedger.bounceBackUnattributedPayment(AMOUNT, paymentId);

    assertThatThrownBy(() -> attributionService.attribute(paymentId, party, true))
        .isInstanceOf(IllegalStateException.class);

    assertThat(unreconciledAccount().getBalance()).isEqualByComparingTo(ZERO);
    assertThat(userCashAccount().getBalance()).isEqualByComparingTo(ZERO);
  }

  @Test
  void attribute_rejectsReturnedPaymentWhenOutboundReturnNotConfirmedCancelled() {
    var party = onboardedParty();
    var paymentId = createReturnedUnattributedPayment();

    assertThatThrownBy(() -> attributionService.attribute(paymentId, party, false))
        .isInstanceOf(IllegalStateException.class);

    assertThat(unreconciledAccount().getBalance()).isEqualByComparingTo(AMOUNT.negate());
    assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(RETURNED);
  }

  private PartyId onboardedParty() {
    userRepository.save(
        User.builder().firstName("Annika").lastName("Tamm").personalCode(PERSONAL_CODE).build());
    onboardingRepository.saveOnboardingStatus(PERSONAL_CODE, PartyId.Type.PERSON, COMPLETED);
    return new PartyId(PartyId.Type.PERSON, PERSONAL_CODE);
  }

  private UUID createReturnedUnattributedPayment() {
    var paymentId =
        paymentRepository.savePaymentData(
            SavingFundPayment.builder()
                .amount(AMOUNT)
                .description("Wise bounce")
                .remitterName("Wise")
                .remitterIdCode("")
                .remitterIban("BE48967056780227")
                .beneficiaryName("TULEVA TÄIENDAV KOGUMISFOND")
                .beneficiaryIdCode("1162")
                .beneficiaryIban("EE711010220306707220")
                .externalId("RMI-test-" + UUID.randomUUID())
                .receivedBefore(Instant.parse("2026-06-12T13:35:00Z"))
                .build());
    paymentRepository.changeStatus(paymentId, RECEIVED);
    paymentRepository.changeStatus(paymentId, TO_BE_RETURNED);
    paymentRepository.changeStatus(paymentId, RETURNED);
    savingsFundLedger.recordUnattributedPayment(AMOUNT, paymentId);
    return paymentId;
  }

  private LedgerAccount unreconciledAccount() {
    return ledgerService.getSystemAccount(UNRECONCILED_BANK_RECEIPTS, TKF100);
  }

  private LedgerAccount incomingPaymentsAccount() {
    return ledgerService.getSystemAccount(INCOMING_PAYMENTS_CLEARING, TKF100);
  }

  private LedgerAccount userCashAccount() {
    return ledgerService.getPartyAccount(PERSONAL_CODE, PERSON, CASH);
  }
}
