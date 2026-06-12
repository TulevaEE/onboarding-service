package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.ERROR;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.OK;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.investment.transaction.FtConfirmation;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.OrderVenue;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FtConfirmationAuditRecorderTest {

  private static final Instant NOW = Instant.parse("2026-06-09T08:00:00Z");
  private static final UUID ORDER_UUID = UUID.fromString("bd83f551-8c79-4193-b92b-18e1dfd0bd29");

  @Mock private TransactionAuditEventRepository auditEventRepository;

  @Test
  void recordVerified_savesOrderLevelEventWithInputsAndResult() {
    TransactionOrder order =
        TransactionOrder.builder()
            .id(42L)
            .fund(TUK75)
            .instrumentIsin("IE000F60HVH9")
            .transactionType(BUY)
            .instrumentType(ETF)
            .orderQuantity(new BigDecimal("40434"))
            .orderVenue(OrderVenue.FT)
            .orderUuid(ORDER_UUID)
            .build();
    FtConfirmation confirmation =
        new FtConfirmation(
            TUK75,
            "IE000F60HVH9",
            LocalDate.parse("2026-06-08"),
            new BigDecimal("40434"),
            new BigDecimal("10.09"));
    FtConfirmationResult result =
        new FtConfirmationResult(
            OK, ERROR, Map.of("orderQuantity", "40434", "priceDeltaPercent", "0.12"));

    new FtConfirmationAuditRecorder(auditEventRepository, Clock.fixed(NOW, ZoneOffset.UTC))
        .recordVerified(order, confirmation, result);

    verify(auditEventRepository)
        .save(
            TransactionAuditEvent.builder()
                .orderId(42L)
                .eventType("FT_CONFIRMATION_VERIFIED")
                .actor("admin")
                .payload(
                    Map.of(
                        "fund", "TUK75",
                        "isin", "IE000F60HVH9",
                        "tradeDate", "2026-06-08",
                        "quantity", "40434",
                        "grossPrice", "10.09",
                        "quantityStatus", "OK",
                        "priceStatus", "ERROR",
                        "details", Map.of("orderQuantity", "40434", "priceDeltaPercent", "0.12")))
                .createdAt(NOW)
                .build());
  }
}
