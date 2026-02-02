package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingFundPaymentUpsertionService {

  private static final Set<SavingFundPayment.Status> MATCHABLE_STATUSES =
      Set.of(CREATED, RETURNED, PROCESSED, FROZEN);

  private final SavingFundPaymentRepository repository;
  private final SavingFundDeadlinesService savingFundDeadlinesService;
  private final NameMatcher nameMatcher;

  public void upsert(
      SavingFundPayment payment, Function<SavingFundPayment, SavingFundPayment.Status> onInsert) {
    upsert(
        payment,
        onInsert,
        p -> {
          throw new IllegalStateException("Unexpected existing payment: id=" + p.getId());
        });
  }

  public void upsert(
      SavingFundPayment payment,
      Function<SavingFundPayment, SavingFundPayment.Status> onInsert,
      Function<SavingFundPayment, SavingFundPayment.Status> onUpdate) {
    if (payment.getExternalId() != null) {
      var existingById = repository.findByExternalId(payment.getExternalId());
      if (existingById.isPresent()) {
        var existing = existingById.get();
        if (isEarlier(payment.getReceivedBefore(), existing.getReceivedBefore())) {
          log.debug(
              "Payment with external ID {} already exists, updating receivedBefore: existing={}, incoming={}",
              payment.getExternalId(),
              existing.getReceivedBefore(),
              payment.getReceivedBefore());
          repository.updateReceivedBefore(existing.getId(), payment.getReceivedBefore());
        } else {
          log.info(
              "Payment with external ID {} already exists, nothing to upsert",
              payment.getExternalId());
        }
        return;
      }
    }

    var existingPayment = findExistingPayment(payment);

    if (existingPayment.isPresent()) {
      var existing = existingPayment.get();
      if (existing.getStatus() != CREATED) {
        log.info(
            "Found existing payment in terminal state, enriching data without status change: id={}, status={}",
            existing.getId(),
            existing.getStatus());
        updatePayment(existing, payment);
      } else {
        log.info("Found existing payment, updating: {}", existing.getId());
        updatePayment(existing, payment);
        var status = onUpdate.apply(payment);
        repository.changeStatus(existing.getId(), status);
      }
    } else {
      log.info("No existing payment found, inserting new payment");
      var paymentId = repository.savePaymentData(payment);
      var status = onInsert.apply(payment);
      repository.changeStatus(paymentId, status);
    }
  }

  public List<SavingFundPayment> getPendingPaymentsForUser(Long userId) {
    return repository
        .findUserPaymentsWithStatus(
            userId, CREATED, RECEIVED, VERIFIED, RESERVED, FROZEN, TO_BE_RETURNED)
        .stream()
        .toList();
  }

  public void cancelUserPayment(Long userId, UUID paymentId) {
    var payment = repository.findById(paymentId).orElseThrow();
    if (!userId.equals(payment.getUserId()) || payment.getCancelledAt() != null) {
      throw new NoSuchElementException();
    }
    var deadline = savingFundDeadlinesService.getCancellationDeadline(payment);
    if (deadline.isBefore(Instant.now())) {
      throw new IllegalStateException("Payment cancellation deadline has passed");
    }
    repository.cancel(paymentId);
  }

  private Optional<SavingFundPayment> findExistingPayment(SavingFundPayment payment) {
    log.debug(
        "Looking for matching payment by description, amount, and remitter IBAN: {}, {}, {}",
        payment.getDescription(),
        payment.getAmount(),
        payment.getRemitterIban());
    return repository.findRecentPayments(payment.getDescription()).stream()
        .filter(p -> p.getExternalId() == null)
        .filter(p -> MATCHABLE_STATUSES.contains(p.getStatus()))
        .filter(p -> p.getAmount().compareTo(payment.getAmount()) == 0)
        .filter(p -> Objects.equals(p.getRemitterIban(), payment.getRemitterIban()))
        .findFirst();
  }

  private void updatePayment(SavingFundPayment existing, SavingFundPayment payment) {
    try {
      var merged = mergePayments(existing, payment);
      repository.updatePaymentData(existing.getId(), merged);
    } catch (IllegalStateException e) {
      throw new IllegalStateException(
          String.format("Error updating payment with ID %s: %s", existing.getId(), e.getMessage()),
          e);
    }
  }

  private SavingFundPayment mergePayments(SavingFundPayment existing, SavingFundPayment payment) {
    return SavingFundPayment.builder()
        .id(existing.getId())
        .amount(mergeAndValidateBigDecimal("amount", existing.getAmount(), payment.getAmount()))
        .currency(mergeAndValidateField("currency", existing.getCurrency(), payment.getCurrency()))
        .description(
            mergeAndValidateField(
                "description", existing.getDescription(), payment.getDescription()))
        .remitterIban(
            mergeAndValidateField(
                "remitterIban", existing.getRemitterIban(), payment.getRemitterIban()))
        .remitterIdCode(
            mergeAndValidateField(
                "remitterIdCode", existing.getRemitterIdCode(), payment.getRemitterIdCode()))
        .remitterName(
            mergeAndValidateName(
                "remitterName", existing.getRemitterName(), payment.getRemitterName()))
        .beneficiaryIban(
            mergeAndValidateField(
                "beneficiaryIban", existing.getBeneficiaryIban(), payment.getBeneficiaryIban()))
        .beneficiaryIdCode(
            mergeAndValidateField(
                "beneficiaryIdCode",
                existing.getBeneficiaryIdCode(),
                payment.getBeneficiaryIdCode()))
        .beneficiaryName(
            mergeAndValidateName(
                "beneficiaryName", existing.getBeneficiaryName(), payment.getBeneficiaryName()))
        .externalId(
            mergeAndValidateField("externalId", existing.getExternalId(), payment.getExternalId()))
        .createdAt(existing.getCreatedAt())
        .receivedBefore(payment.getReceivedBefore())
        .status(existing.getStatus())
        .statusChangedAt(existing.getStatusChangedAt())
        .build();
  }

  private java.math.BigDecimal mergeAndValidateBigDecimal(
      String fieldName, java.math.BigDecimal existingValue, java.math.BigDecimal newValue) {
    if (existingValue == null) {
      return newValue;
    } else if (existingValue.compareTo(newValue) != 0) {
      throw new IllegalStateException(
          String.format(
              "Payment field mismatch: %s existing='%s', incoming='%s'",
              fieldName, existingValue, newValue));
    }
    return existingValue;
  }

  private <T> T mergeAndValidateField(String fieldName, T existingValue, T newValue) {
    if (existingValue == null) {
      return newValue;
    } else if (newValue == null) {
      // We have a value for something already, lets not override it with null
      return existingValue;
    } else if (!existingValue.equals(newValue)) {
      throw new IllegalStateException(
          String.format(
              "Payment field mismatch: %s existing='%s', incoming='%s'",
              fieldName, existingValue, newValue));
    }
    return existingValue;
  }

  private String mergeAndValidateName(String fieldName, String existingValue, String newValue) {
    if (existingValue == null) {
      return newValue;
    } else if (newValue == null) {
      return existingValue;
    } else if (!nameMatcher.isSameName(existingValue, newValue)) {
      throw new IllegalStateException(
          String.format(
              "Payment field mismatch: %s existing='%s', incoming='%s'",
              fieldName, existingValue, newValue));
    }
    return existingValue;
  }

  private boolean isEarlier(Instant incoming, Instant existing) {
    return incoming != null && (existing == null || incoming.isBefore(existing));
  }
}
