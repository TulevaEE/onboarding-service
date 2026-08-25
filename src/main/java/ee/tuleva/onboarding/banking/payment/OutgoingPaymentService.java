package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.ATTEMPTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.EXECUTED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.FAILED;
import static ee.tuleva.onboarding.banking.payment.OutgoingPaymentStatus.SUBMITTED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what we send to the bank.
 *
 * <p>Every write runs in its own transaction. The callers publish their payment events from inside
 * an open transaction and the listener handles them synchronously, so a row written on the caller's
 * transaction would roll back together with it — losing exactly the case this log exists for: the
 * bank accepted the file and our commit then failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingPaymentService {

  private final OutgoingPaymentRepository outgoingPaymentRepository;
  private final Clock clock;

  /**
   * Claims the endToEndId before the payment is sent. Refuses a second attempt on an id that is
   * already submitted, or one still in flight — resending either could pay the same money twice.
   */
  @Transactional(propagation = REQUIRES_NEW)
  public void recordAttempt(
      PaymentRequest paymentRequest,
      OutgoingPaymentType paymentType,
      @Nullable UUID sourceId,
      @Nullable UUID batchId,
      String submittedBody) {
    var endToEndId = paymentRequest.endToEndId();
    var existing = outgoingPaymentRepository.findByEndToEndId(endToEndId).orElse(null);

    if (existing != null && existing.getStatus() != FAILED) {
      throw new OutgoingPaymentBlockedException(endToEndId, existing.getStatus());
    }

    var row = existing != null ? existing : new OutgoingPayment();
    row.setEndToEndId(endToEndId);
    row.setPaymentType(paymentType);
    row.setSourceId(sourceId);
    row.setBatchId(batchId);
    row.setRemitterIban(paymentRequest.remitterIban());
    row.setBeneficiaryIban(paymentRequest.beneficiaryIban());
    row.setAmount(paymentRequest.amount());
    row.setCurrency("EUR");
    row.setBodyHash(sha256(submittedBody));
    row.setStatus(ATTEMPTED);
    row.setFailureReason(null);
    row.setAttemptedAt(Instant.now(clock));
    row.setResolvedAt(null);
    outgoingPaymentRepository.save(row);
  }

  @Transactional(propagation = REQUIRES_NEW)
  public void recordSubmitted(String endToEndId) {
    resolve(endToEndId, SUBMITTED, null);
  }

  @Transactional(propagation = REQUIRES_NEW)
  public void recordFailed(String endToEndId, String reason) {
    resolve(endToEndId, FAILED, reason);
  }

  /**
   * The money actually left the account. Without this the reconciler could only ever see
   * "submitted", and would report every payment as unexecuted once its deadline passed.
   */
  @Transactional(propagation = REQUIRES_NEW)
  public void recordExecuted(String endToEndId) {
    resolve(endToEndId, EXECUTED, null);
  }

  private void resolve(String endToEndId, OutgoingPaymentStatus status, @Nullable String reason) {
    outgoingPaymentRepository
        .findByEndToEndId(endToEndId)
        .ifPresentOrElse(
            row -> {
              row.setStatus(status);
              row.setFailureReason(reason);
              row.setResolvedAt(Instant.now(clock));
              outgoingPaymentRepository.save(row);
            },
            () ->
                log.error(
                    "No outgoing payment row to resolve, this should not happen: endToEndId={}, status={}",
                    endToEndId,
                    status));
  }

  private static String sha256(String body) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(body.getBytes(UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
