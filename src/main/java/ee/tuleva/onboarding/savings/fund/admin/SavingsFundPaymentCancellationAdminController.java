package ee.tuleva.onboarding.savings.fund.admin;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.savings.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.PaymentCancellationRefusedException;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentCancellationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class SavingsFundPaymentCancellationAdminController {

  private final AdminTokenValidator tokenValidator;
  private final SavingFundPaymentCancellationService savingFundPaymentCancellationService;

  @PostMapping("/savings-fund/payments/{paymentId}/cancel")
  public Map<String, String> cancelSavingsFundPayment(
      @RequestHeader("X-Admin-Token") String token,
      @PathVariable UUID paymentId,
      @Valid @RequestBody CancelSavingsFundPaymentRequest request) {

    tokenValidator.validateWithOpsAccess(token);

    var payment = cancelPayment(paymentId, request.reason());
    log.info(
        "Admin cancelled savings fund payment: paymentId={}, status={}",
        paymentId,
        payment.getStatus());

    return Map.of(
        "paymentId", payment.getId().toString(),
        "status", payment.getStatus().name(),
        "cancelledAt",
            requireNonNull(
                    payment.getCancelledAt(),
                    "Cancelled payment has no cancelledAt: paymentId=" + paymentId)
                .toString());
  }

  private SavingFundPayment cancelPayment(UUID paymentId, String reason) {
    try {
      return savingFundPaymentCancellationService.cancelPayment(paymentId, reason);
    } catch (NoSuchElementException e) {
      throw refusedCancellation(NOT_FOUND, paymentId, e);
    } catch (PaymentCancellationRefusedException e) {
      throw refusedCancellation(CONFLICT, paymentId, e);
    } catch (IllegalArgumentException e) {
      throw refusedCancellation(BAD_REQUEST, paymentId, e);
    }
  }

  private ResponseStatusException refusedCancellation(
      HttpStatus status, UUID paymentId, RuntimeException e) {
    log.info(
        "Admin savings fund payment cancellation refused: paymentId={}, error={}",
        paymentId,
        e.getMessage());
    return new ResponseStatusException(status, e.getMessage(), e);
  }
}
