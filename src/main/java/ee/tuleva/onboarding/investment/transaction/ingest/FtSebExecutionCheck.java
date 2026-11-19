package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.CONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCHECKED;
import static ee.tuleva.onboarding.investment.transaction.FtVerificationStatus.UNCONFIRMED;
import static ee.tuleva.onboarding.investment.transaction.ingest.FtOrderSelection.tradeDate;

import ee.tuleva.onboarding.investment.calendar.Target2Calendar;
import ee.tuleva.onboarding.investment.transaction.FtConfirmationResult;
import ee.tuleva.onboarding.investment.transaction.FtVerificationStatus;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

@NullMarked
@Slf4j
@Component
@RequiredArgsConstructor
class FtSebExecutionCheck {

  private static final int CONFIRMATION_PENDING_BUSINESS_DAYS = 1;
  private static final FtConfirmationResult CONFIRMED_BY_FT =
      new FtConfirmationResult(CONFIRMED, CONFIRMED, Map.of());

  private final FtPriceCheck priceCheck;
  private final Target2Calendar target2Calendar;
  private final Clock clock;

  boolean confirmationOverdue(TransactionOrder order) {
    return LocalDate.now(clock).isAfter(confirmationDueBy(order));
  }

  FtExecutedTrade confirmed(TransactionOrder order, SebExecutedTotal executed) {
    return trade(order, executed, CONFIRMED_BY_FT);
  }

  FtExecutedTrade unconfirmed(TransactionOrder order, SebExecutedTotal executed) {
    Map<String, String> details = new LinkedHashMap<>();
    details.put("confirmationDueBy", confirmationDueBy(order).toString());
    FtVerificationStatus priceStatus = checkPrice(order, executed, details);
    return trade(
        order, executed, new FtConfirmationResult(UNCONFIRMED, priceStatus, Map.copyOf(details)));
  }

  private LocalDate confirmationDueBy(TransactionOrder order) {
    return target2Calendar.addBusinessDays(tradeDate(order), CONFIRMATION_PENDING_BUSINESS_DAYS);
  }

  private FtVerificationStatus checkPrice(
      TransactionOrder order, SebExecutedTotal executed, Map<String, String> details) {
    BigDecimal sebPrice = executed.averagePrice();
    if (sebPrice == null) {
      details.put("verificationError", "SEB reported no price for the trade");
      return UNCHECKED;
    }
    try {
      return priceCheck.check(order.getInstrumentIsin(), tradeDate(order), sebPrice, details);
    } catch (RuntimeException e) {
      log.error(
          "SEB price of an FT trade without a confirmation could not be checked: orderUuid={},"
              + " error={}",
          order.getOrderUuid(),
          e.getMessage(),
          e);
      details.put("verificationError", e.getMessage() == null ? e.toString() : e.getMessage());
      return UNCHECKED;
    }
  }

  private static FtExecutedTrade trade(
      TransactionOrder order, SebExecutedTotal executed, FtConfirmationResult result) {
    return new FtExecutedTrade(
        order.getFund(),
        order.getInstrumentIsin(),
        tradeDate(order),
        order.getOrderUuid(),
        executed.quantity(),
        executed.averagePrice(),
        result);
  }
}
