package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.INFO;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PHANTOM_DEBIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentService;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingPaymentMatcherTest {

  private static final String END_TO_END_ID = "abc123";
  private static final String BANK_FEE_IBAN = "EE333333333333333333";

  @Mock private OutgoingPaymentRepository outgoingPaymentRepository;
  @Mock private OutgoingPaymentService outgoingPaymentService;
  @Mock private PaymentCheckService paymentCheckService;
  @Mock private SebAccountConfiguration sebAccountConfiguration;

  @InjectMocks private OutgoingPaymentMatcher matcher;

  @BeforeEach
  void noAllowlistsByDefault() {
    lenient().when(sebAccountConfiguration.getBankFeeIbans()).thenReturn(List.of());
    lenient().when(sebAccountConfiguration.getOwnAccountIbans()).thenReturn(List.of());
    lenient().when(sebAccountConfiguration.getRegistrarIbans()).thenReturn(List.of());
  }

  @Test
  void aMatchedDebitMarksThePaymentExecuted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, "EE222222222222222222"));

    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
    verify(paymentCheckService, never()).record(any(), any(), any(), any());
  }

  @Test
  void aDebitForADifferentAmountThanWeAuthorisedIsReported() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("11.00"), END_TO_END_ID, "EE222222222222222222"));

    verify(paymentCheckService).record(eq(DEBIT_MISMATCH), eq(HOLD), eq(END_TO_END_ID), any());
    // The money did move, so the payment is still executed -- it is just wrong.
    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
  }

  @Test
  void aDebitWithNothingBehindItIsAPhantom() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID)).thenReturn(Optional.empty());

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, "EE222222222222222222"));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), any(), any());
  }

  @Test
  void aDebitWithNoEndToEndIdIsAPhantomToo() {
    matcher.match(debit(new BigDecimal("10.00"), null, "EE222222222222222222"));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), any(), any());
    verifyNoInteractions(outgoingPaymentRepository);
  }

  @Test
  void aBankFeeIsRecordedWithoutPagingAnyone() {
    when(sebAccountConfiguration.getBankFeeIbans()).thenReturn(List.of(BANK_FEE_IBAN));

    matcher.match(debit(new BigDecimal("10.00"), null, BANK_FEE_IBAN));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(INFO), any(), any());
  }

  @Test
  void anIncomingPaymentIsNotADebitAndIsIgnored() {
    matcher.match(debit(new BigDecimal("-10.00"), END_TO_END_ID, "EE222222222222222222"));

    verifyNoInteractions(outgoingPaymentRepository, paymentCheckService, outgoingPaymentService);
  }

  private static OutgoingPayment logged(BigDecimal amount, OutgoingPaymentStatus status) {
    var row = new OutgoingPayment();
    row.setEndToEndId(END_TO_END_ID);
    row.setAmount(amount);
    row.setStatus(status);
    return row;
  }

  /** Statement debits arrive negative, which is why the amount is negated here. */
  private static SavingFundPayment debit(BigDecimal amount, String endToEndId, String beneficiary) {
    return SavingFundPayment.builder()
        .id(UUID.randomUUID())
        .amount(amount.negate())
        .endToEndId(endToEndId)
        .beneficiaryIban(beneficiary)
        .build();
  }
}
