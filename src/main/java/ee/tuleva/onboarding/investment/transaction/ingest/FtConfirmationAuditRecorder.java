package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Component
@RequiredArgsConstructor
class FtConfirmationAuditRecorder {

  static final String FT_CONFIRMATION_VERIFIED = "FT_CONFIRMATION_VERIFIED";

  private static final String ADMIN_ACTOR = "admin";

  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;

  void recordVerified(
      TransactionOrder order, FtConfirmation confirmation, FtConfirmationResult result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", confirmation.fund().name());
    payload.put("isin", confirmation.isin());
    payload.put("tradeDate", confirmation.tradeDate().toString());
    payload.put("quantity", confirmation.quantity().toPlainString());
    payload.put("grossPrice", confirmation.grossPrice().toPlainString());
    payload.put("type", confirmation.type().name());
    if (confirmation.account() != null) {
      payload.put("account", confirmation.account());
    }
    payload.put("quantityStatus", result.quantityStatus().name());
    payload.put("priceStatus", result.priceStatus().name());
    payload.put("details", result.details());

    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .orderId(order.getId())
            .batch(order.getBatch())
            .eventType(FT_CONFIRMATION_VERIFIED)
            .actor(ADMIN_ACTOR)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }
}
