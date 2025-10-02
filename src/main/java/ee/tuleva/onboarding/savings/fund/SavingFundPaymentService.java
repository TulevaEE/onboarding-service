package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.*;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingFundPaymentService {

  private final SavingFundPaymentRepository repository;

  public void upsert(SavingFundPayment payment) {
    // Check if payment with this external ID already exists
    if (payment.getExternalId() != null) {
      var existingById = repository.findByExternalId(payment.getExternalId());
      if (existingById.isPresent()) {
        log.info(
            "Payment with external ID {} already exists, nothing to upsert",
            payment.getExternalId());
        return;
      }
    }

    UUID paymentId = upsertPayment(payment);

    // Outgoing payments (amount <= 0) go directly to PROCESSED, no need to process further
    var status = payment.getAmount().compareTo(BigDecimal.ZERO) > 0 ? RECEIVED : PROCESSED;
    repository.changeStatus(paymentId, status);
  }

  public List<SavingFundPayment> getPendingPaymentsForUser(
      AuthenticatedPerson authenticatedPerson) {
    var pendingStatuses = List.of(CREATED, RECEIVED, VERIFIED);

    return repository.findUserPayments(authenticatedPerson.getUserId()).stream()
        .filter(p -> pendingStatuses.contains(p.getStatus()))
        .toList();
  }

  private UUID upsertPayment(SavingFundPayment payment) {
    var existingPayment = findExistingPayment(payment);
    UUID paymentId;

    if (existingPayment.isPresent()) {
      log.info("Found existing payment, updating: {}", existingPayment.get().getId());
      updatePayment(existingPayment.get(), payment);
      paymentId = existingPayment.get().getId();
    } else {
      log.info("No existing payment found, inserting new payment");
      paymentId = repository.savePaymentData(payment);
    }
    return paymentId;
  }

  private Optional<SavingFundPayment> findExistingPayment(SavingFundPayment payment) {
    log.debug("Looking for matching payment by description: {}", payment.getDescription());
    return repository.findRecentPayments(payment.getDescription()).stream()
        .filter(p -> p.getExternalId() == null)
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
            mergeAndValidateField(
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
            mergeAndValidateField(
                "beneficiaryName", existing.getBeneficiaryName(), payment.getBeneficiaryName()))
        .externalId(
            mergeAndValidateField("externalId", existing.getExternalId(), payment.getExternalId()))
        .createdAt(existing.getCreatedAt())
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
}
