package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.INFO;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PHANTOM_DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.banking.ManagementCompanies;
import ee.tuleva.onboarding.banking.StatementDebit;
import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentService;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutgoingPaymentMatcherTest {

  private static final String END_TO_END_ID = "abc123";
  private static final String AUTHORISED_IBAN = "EE222222222222222222";
  private static final String BANK_FEE_IBAN = "EE333333333333333333";
  private static final String MANAGEMENT_COMPANY = "Tuleva Fondid AS";

  @Mock private OutgoingPaymentRepository outgoingPaymentRepository;
  @Mock private OutgoingPaymentService outgoingPaymentService;
  @Mock private PaymentCheckService paymentCheckService;
  @Mock private SebAccountConfiguration sebAccountConfiguration;

  private OutgoingPaymentMatcher matcher;

  @BeforeEach
  void noAllowlistsByDefault() {
    lenient().when(sebAccountConfiguration.getBankFeeIbans()).thenReturn(List.of());
    lenient().when(sebAccountConfiguration.getOwnAccountIbans()).thenReturn(List.of());
    lenient().when(sebAccountConfiguration.getRegistrarIbans()).thenReturn(List.of());
    matcher =
        new OutgoingPaymentMatcher(
            outgoingPaymentRepository,
            outgoingPaymentService,
            paymentCheckService,
            sebAccountConfiguration,
            new ManagementCompanies(sebAccountConfiguration));
  }

  @Test
  void aMatchedDebitMarksThePaymentExecuted() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, AUTHORISED_IBAN));

    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
    verify(paymentCheckService, never()).record(any(), any(), any(), any());
  }

  @Test
  void aDebitForADifferentAmountThanWeAuthorisedIsReported() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("11.00"), END_TO_END_ID, AUTHORISED_IBAN));

    verify(paymentCheckService).record(eq(DEBIT_MISMATCH), eq(HOLD), eq(END_TO_END_ID), any());
    // The money did move, so the payment is still executed -- it is just wrong.
    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
  }

  // The end-to-end id is ours and the bank echoes it back, so it identifies the payment but proves
  // nothing about where the money went. Matching on it alone would mark this executed in silence.
  @Test
  void aDebitToADifferentAccountThanWeAuthorisedIsReported() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, "EE444444444444444444"));

    verify(paymentCheckService).record(eq(DEBIT_MISMATCH), eq(HOLD), eq(END_TO_END_ID), any());
    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
  }

  // The end-to-end id is ours, so on its own it proves only that the bank echoed our reference
  // back. Without a beneficiary iban there is nothing to corroborate where the money went, and
  // passing it in silence is exactly the hole these checks exist to close.
  @Test
  void aDebitThatDoesNotSayWhichAccountWasPaidCannotBeCorroborated() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID))
        .thenReturn(Optional.of(logged(new BigDecimal("10.00"), OutgoingPaymentStatus.SUBMITTED)));

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, null));

    verify(paymentCheckService).record(eq(DEBIT_MISMATCH), eq(HOLD), eq(END_TO_END_ID), any());
    verify(outgoingPaymentService).recordExecuted(END_TO_END_ID);
  }

  @Test
  void anUnidentifiedDebitWithNoBeneficiaryIsNotTreatedAsAllowlisted() {
    lenient().when(sebAccountConfiguration.getBankFeeIbans()).thenReturn(List.of(BANK_FEE_IBAN));

    matcher.match(debit(new BigDecimal("10.00"), null, null));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), any(), any());
  }

  @Test
  void aDebitWithNothingBehindItIsAPhantom() {
    when(outgoingPaymentRepository.findByEndToEndId(END_TO_END_ID)).thenReturn(Optional.empty());

    matcher.match(debit(new BigDecimal("10.00"), END_TO_END_ID, AUTHORISED_IBAN));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), any(), any());
  }

  @Test
  void aDebitWithNoEndToEndIdIsAPhantomToo() {
    matcher.match(debit(new BigDecimal("10.00"), null, AUTHORISED_IBAN));

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
  void aManagementFeeIsRecordedWithoutPagingAnyone() {
    when(sebAccountConfiguration.isManagementCompany(MANAGEMENT_COMPANY)).thenReturn(true);

    matcher.match(managementFeeDebit());

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(INFO), any(), any());
    verifyNoInteractions(outgoingPaymentRepository);
  }

  @Test
  void aDebitToSomeoneElseThatOnlyLooksLikeAFeeIsStillAPhantom() {
    matcher.match(
        new StatementDebit(
            "seb-entry",
            new BigDecimal("-742.34"),
            AUTHORISED_IBAN,
            "Impostor OU",
            "Valitsemistasu 02.-28.02.26",
            null));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), any(), any());
  }

  @Test
  void anIncomingPaymentIsNotADebitAndIsIgnored() {
    matcher.match(debit(new BigDecimal("-10.00"), END_TO_END_ID, AUTHORISED_IBAN));

    verifyNoInteractions(outgoingPaymentRepository, paymentCheckService, outgoingPaymentService);
  }

  @Test
  void anUnbackedDebitIsKeyedByItsStatementEntry() {
    matcher.match(debit(new BigDecimal("10.00"), null, AUTHORISED_IBAN));

    verify(paymentCheckService).record(eq(PHANTOM_DEBIT), eq(HOLD), eq("seb-entry"), any());
  }

  @Test
  void twoUnidentifiedDebitsDoNotShareAKeyAndSilenceEachOther() {
    var keys = recordedKeys();

    matcher.match(
        new StatementDebit(null, new BigDecimal("-10.00"), AUTHORISED_IBAN, null, null, null));
    matcher.match(
        new StatementDebit(null, new BigDecimal("-20.00"), AUTHORISED_IBAN, null, null, null));

    assertThat(keys).hasSize(2).doesNotHaveDuplicates();
  }

  private List<String> recordedKeys() {
    var keys = new ArrayList<String>();
    doAnswer(
            call -> {
              keys.add(call.getArgument(2));
              return null;
            })
        .when(paymentCheckService)
        .record(any(), any(), any(), any());
    return keys;
  }

  private static OutgoingPayment logged(BigDecimal amount, OutgoingPaymentStatus status) {
    var row = new OutgoingPayment();
    row.setEndToEndId(END_TO_END_ID);
    row.setAmount(amount);
    row.setBeneficiaryIban(AUTHORISED_IBAN);
    row.setStatus(status);
    return row;
  }

  /** Statement debits arrive negative, which is why the amount is negated here. */
  private static StatementDebit debit(BigDecimal amount, String endToEndId, String beneficiary) {
    return new StatementDebit("seb-entry", amount.negate(), beneficiary, null, null, endToEndId);
  }

  private static StatementDebit managementFeeDebit() {
    return new StatementDebit(
        "seb-entry",
        new BigDecimal("-742.34"),
        AUTHORISED_IBAN,
        MANAGEMENT_COMPANY,
        "Valitsemistasu 02.-28.02.26",
        null);
  }
}
