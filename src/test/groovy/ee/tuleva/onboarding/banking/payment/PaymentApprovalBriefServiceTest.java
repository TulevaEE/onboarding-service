package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.BankAccountType.WITHDRAWAL_EUR;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.PAYOUT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentType.REDEMPTION_TRANSFER;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import ee.tuleva.onboarding.banking.BankAccount;
import ee.tuleva.onboarding.banking.BankAccounts;
import ee.tuleva.onboarding.banking.seb.SebAccountBalanceReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentApprovalBriefServiceTest {

  private static final LocalDate DATE = LocalDate.of(2026, 4, 10);
  private static final String IBAN = "EE222222222222222222";
  private static final BankAccount ACCOUNT =
      new BankAccount(IBAN, WITHDRAWAL_EUR, TKF100, "gateway-client");

  @Mock OutgoingPaymentRepository outgoingPaymentRepository;
  @Mock BankAccounts bankAccounts;
  @Mock SebAccountBalanceReader balanceReader;

  private final Clock clock =
      Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneId.of("Europe/Tallinn"));

  private PaymentApprovalBriefService service() {
    return new PaymentApprovalBriefService(
        outgoingPaymentRepository, bankAccounts, balanceReader, clock);
  }

  // A payment already executed was approved earlier and is no longer on the bank's pending screen,
  // so counting it would make the brief disagree with what the signatory is looking at.
  @Test
  void anAlreadyExecutedPaymentIsNotOnThePendingScreenSoItIsNotCounted() {
    givenAccountResolves();
    givenPaymentsToday(payment(SUBMITTED, PAYOUT, "100.00"), payment(EXECUTED, PAYOUT, "999.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.totalPaymentCount()).isEqualTo(1);
    assertThat(brief.grandTotal()).isEqualByComparingTo("100.00");
  }

  @Test
  void theProjectedBalanceIsWhatTheAccountIsLeftWithOnceThesePaymentsExecute() {
    givenAccountResolves();
    given(balanceReader.available(ACCOUNT)).willReturn(Optional.of(new BigDecimal("1000.00")));
    givenPaymentsToday(payment(SUBMITTED, PAYOUT, "300.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(
            account -> assertThat(account.projectedBalance()).isEqualByComparingTo("700.00"));
  }

  @Test
  void anAccountThatWouldGoNegativeNeedsAttention() {
    givenAccountResolves();
    given(balanceReader.available(ACCOUNT)).willReturn(Optional.of(new BigDecimal("100.00")));
    givenPaymentsToday(payment(SUBMITTED, PAYOUT, "300.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(account -> assertThat(account.goesNegative()).isTrue());
    assertThat(brief.attention()).isTrue();
  }

  // In flight means the call never returned a verdict: the payment may or may not have reached the
  // bank, which is exactly the kind of day that deserves a proper look.
  @Test
  void aPaymentStillInFlightNeedsAttention() {
    givenAccountResolves();
    givenPaymentsToday(payment(ATTEMPTED, PAYOUT, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.attention()).isTrue();
  }

  @Test
  void aCleanDayWithNothingHeldNeedsNoAttention() {
    givenAccountResolves();
    givenPaymentsToday(payment(SUBMITTED, PAYOUT, "100.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.attention()).isFalse();
  }

  @Test
  void heldPaymentsAreCountedWithTheirDistinctReasonsAndNeedAttention() {
    givenAccountResolves();
    givenPaymentsToday(payment(SUBMITTED, PAYOUT, "100.00"));

    var brief =
        service()
            .build(
                DATE,
                List.of(
                    new PaymentApprovalBriefService.PaymentHold("PAYOUT", "iban not the party's"),
                    new PaymentApprovalBriefService.PaymentHold("PAYOUT", "iban not the party's"),
                    new PaymentApprovalBriefService.PaymentHold(
                        "RETURN", "name will not resolve")));

    assertThat(brief.heldCount()).isEqualTo(3);
    assertThat(brief.heldReasons())
        .containsExactlyInAnyOrder("iban not the party's", "name will not resolve");
    assertThat(brief.attention()).isTrue();
  }

  @Test
  void paymentsAreSummarisedPerFlowWithinAnAccount() {
    givenAccountResolves();
    givenPaymentsToday(
        payment(SUBMITTED, PAYOUT, "100.00"),
        payment(SUBMITTED, PAYOUT, "50.00"),
        payment(SUBMITTED, REDEMPTION_TRANSFER, "400.00"));

    var brief = service().build(DATE, List.of());

    assertThat(brief.accounts())
        .singleElement()
        .satisfies(
            account ->
                assertThat(account.flows())
                    .containsExactly(
                        new PaymentApprovalBrief.FlowSummary(
                            "payouts to clients", 2, new BigDecimal("150.00")),
                        new PaymentApprovalBrief.FlowSummary(
                            "to withdrawal account", 1, new BigDecimal("400.00"))));
  }

  private void givenAccountResolves() {
    lenient().when(bankAccounts.find(IBAN)).thenReturn(Optional.of(ACCOUNT));
    lenient().when(balanceReader.available(any(BankAccount.class))).thenReturn(Optional.empty());
  }

  private void givenPaymentsToday(OutgoingPayment... payments) {
    given(outgoingPaymentRepository.findByAttemptedAtBetween(any(), any()))
        .willReturn(List.of(payments));
  }

  private static OutgoingPayment payment(
      OutgoingPaymentStatus status, OutgoingPaymentType type, String amount) {
    return OutgoingPayment.builder()
        .endToEndId("E2E-" + amount + "-" + type)
        .paymentType(type)
        .remitterIban(IBAN)
        .beneficiaryIban("EE333333333333333333")
        .amount(new BigDecimal(amount))
        .currency("EUR")
        .bodyHash("hash")
        .status(status)
        .attemptedAt(Instant.parse("2026-04-10T09:00:00Z"))
        .build();
  }
}
