package ee.tuleva.onboarding.banking.check.payment;

import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.HOLD;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckSeverity.INFO;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.DEBIT_MISMATCH;
import static ee.tuleva.onboarding.banking.check.payment.PaymentCheckType.PHANTOM_DEBIT;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static java.nio.charset.StandardCharsets.UTF_8;

import ee.tuleva.onboarding.banking.StatementDebit;
import ee.tuleva.onboarding.banking.payment.OutgoingPayment;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentRepository;
import ee.tuleva.onboarding.banking.payment.OutgoingPaymentService;
import ee.tuleva.onboarding.banking.seb.SebAccountConfiguration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutgoingPaymentMatcher {
  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final OutgoingPaymentService outgoingPaymentService;
  private final PaymentCheckService paymentCheckService;
  private final SebAccountConfiguration sebAccountConfiguration;

  public void match(StatementDebit debit) {
    if (debit.amount().signum() >= 0) {
      return;
    }
    var debited = debit.amount().negate();
    var endToEndId = debit.endToEndId();

    if (endToEndId == null || endToEndId.isBlank()) {
      reportUnbacked(debit, keyOf(debit), "the debit carries no end-to-end id to match on");
      return;
    }

    var logged = outgoingPaymentRepository.findByEndToEndId(endToEndId).orElse(null);
    if (logged == null) {
      reportUnbacked(debit, endToEndId, "no outgoing payment was ever recorded for this debit");
      return;
    }

    var beneficiaryIban = debit.beneficiaryIban();
    var wrongAmount = debited.compareTo(logged.getAmount()) != 0;
    if (beneficiaryIban == null || beneficiaryIban.isBlank()) {
      paymentCheckService.record(DEBIT_MISMATCH, HOLD, endToEndId, unverifiableDetail(wrongAmount));
      markExecuted(logged);
      return;
    }

    var wrongBeneficiary = !beneficiaryIban.equalsIgnoreCase(logged.getBeneficiaryIban());
    if (wrongAmount || wrongBeneficiary) {
      paymentCheckService.record(
          DEBIT_MISMATCH, HOLD, endToEndId, mismatchDetail(wrongAmount, wrongBeneficiary));
    }
    markExecuted(logged);
  }

  private static String unverifiableDetail(boolean wrongAmount) {
    var unverifiable =
        "the statement does not say which account the bank paid, so the beneficiary cannot be"
            + " corroborated";
    return wrongAmount
        ? "the bank debited an amount other than the one we authorised, and " + unverifiable
        : unverifiable;
  }

  private static String mismatchDetail(boolean wrongAmount, boolean wrongBeneficiary) {
    if (wrongAmount && wrongBeneficiary) {
      return "the bank debited a different amount, to a different account, than we authorised";
    }
    return wrongAmount
        ? "the bank debited an amount other than the one we authorised"
        : "the bank paid an account other than the one we authorised";
  }

  private void markExecuted(OutgoingPayment logged) {
    if (logged.getStatus() != EXECUTED) {
      outgoingPaymentService.recordExecuted(logged.getEndToEndId());
    }
  }

  private void reportUnbacked(StatementDebit debit, String key, String detail) {
    var beneficiary = debit.beneficiaryIban();
    var legitimate =
        beneficiary != null
            && (contains(sebAccountConfiguration.getBankFeeIbans(), beneficiary)
                || contains(sebAccountConfiguration.getOwnAccountIbans(), beneficiary)
                || contains(sebAccountConfiguration.getRegistrarIbans(), beneficiary));

    paymentCheckService.record(
        PHANTOM_DEBIT,
        legitimate ? INFO : HOLD,
        key,
        legitimate ? "a known non-pipeline debit: " + detail : detail);
  }

  private static String keyOf(StatementDebit debit) {
    var entryId = debit.entryId();
    return entryId != null && !entryId.isBlank() ? entryId : digestOf(debit);
  }

  private static String digestOf(StatementDebit debit) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var canonical = debit.beneficiaryIban() + ":" + debit.amount().toPlainString();
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static boolean contains(List<String> ibans, String iban) {
    return ibans.stream().anyMatch(iban::equalsIgnoreCase);
  }
}
