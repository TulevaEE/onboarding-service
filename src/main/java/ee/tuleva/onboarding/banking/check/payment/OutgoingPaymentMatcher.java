package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.INFO;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PHANTOM_DEBIT;

import ee.tuleva.onboarding.banking.StatementDebit;
import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentService;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ties every debit we see on the statement back to a payment we recorded sending.
 *
 * <p>Three outcomes, and each answers a question nothing else does:
 *
 * <ul>
 *   <li><b>Matched</b> — the payment is marked executed. Until something does this, the log only
 *       ever says "the bank accepted the file", and the reconciler would report every payment as
 *       unexecuted once its deadline passed.
 *   <li><b>Amount differs</b> — the bank moved an amount other than the one we authorised. Neither
 *       the aggregate reconciliation nor the payout path catches this on its own: the ledger books
 *       the bank's own figure, so both sides move together and agree.
 *   <li><b>No row at all</b> — money left an account of ours with nothing behind it. This is the
 *       phantom case, and it is the reason to look at every debit rather than only the payouts.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutgoingPaymentMatcher {

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final OutgoingPaymentService outgoingPaymentService;
  private final PaymentCheckService paymentCheckService;
  private final SebAccountConfiguration sebAccountConfiguration;

  public void match(StatementDebit debit) {
    if (debit.amount().compareTo(BigDecimal.ZERO) >= 0) {
      return;
    }
    var debited = debit.amount().negate();
    var endToEndId = debit.endToEndId();

    if (endToEndId == null || endToEndId.isBlank()) {
      reportUnbacked(debit, "the debit carries no end-to-end id to match on");
      return;
    }

    var logged = outgoingPaymentRepository.findByEndToEndId(endToEndId).orElse(null);
    if (logged == null) {
      reportUnbacked(debit, "no outgoing payment was ever recorded for this debit");
      return;
    }

    if (debited.compareTo(logged.getAmount()) != 0) {
      paymentCheckService.record(
          DEBIT_MISMATCH,
          HOLD,
          endToEndId,
          "the bank debited an amount other than the one we authorised");
    }
    markExecuted(logged);
  }

  private void markExecuted(OutgoingPayment logged) {
    if (logged.getStatus() != ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED) {
      outgoingPaymentService.recordExecuted(logged.getEndToEndId());
    }
  }

  /**
   * A debit our pipeline did not create is not automatically wrong — bank fees and movements
   * between our own accounts are legitimate and were never submitted through this path. Those are
   * recorded without paging anyone; everything else is a phantom.
   */
  private void reportUnbacked(StatementDebit debit, String detail) {
    var beneficiary = debit.beneficiaryIban();
    var legitimate =
        contains(sebAccountConfiguration.getBankFeeIbans(), beneficiary)
            || contains(sebAccountConfiguration.getOwnAccountIbans(), beneficiary)
            || contains(sebAccountConfiguration.getRegistrarIbans(), beneficiary);

    paymentCheckService.record(
        PHANTOM_DEBIT,
        legitimate ? INFO : HOLD,
        String.valueOf(debit.paymentId()),
        legitimate ? "a known non-pipeline debit: " + detail : detail);
  }

  private static boolean contains(List<String> ibans, String iban) {
    return ibans.stream().anyMatch(iban::equalsIgnoreCase);
  }
}
