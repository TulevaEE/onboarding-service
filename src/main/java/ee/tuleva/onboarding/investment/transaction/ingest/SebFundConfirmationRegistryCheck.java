package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryComparison;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebFundConfirmationRegistryCheck {

  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;

  RegistryComparison compare(SebFundConfirmation confirmation) {
    return registered(confirmation)
        .map(
            trade ->
                RegistryComparison.of(
                    tradeDifferences(confirmation, trade.order())
                        .amount(
                            "kogus",
                            confirmation.quantity(),
                            stripped(trade.execution().getExecutedQuantity()))
                        .amount(
                            "hind",
                            confirmation.price(),
                            stripped(trade.execution().getUnitPrice()))
                        .amount(
                            "kogumaksumus",
                            confirmation.totalAmount(),
                            stripped(trade.execution().getTotalConsideration()))
                        .list()))
        .orElse(RegistryComparison.NOT_IN_REGISTRY);
  }

  Optional<TransactionExecution> executionOfTheSameTrade(SebFundConfirmation confirmation) {
    return registered(confirmation)
        .filter(trade -> isTheSameTrade(confirmation, trade.order()))
        .map(RegisteredTrade::execution);
  }

  private Optional<RegisteredTrade> registered(SebFundConfirmation confirmation) {
    return executionRepository
        .findByBrokerTransactionId(confirmation.tradeReference())
        .flatMap(
            execution ->
                orderRepository
                    .findById(execution.getOrderId())
                    .map(order -> new RegisteredTrade(execution, order)));
  }

  private static boolean isTheSameTrade(SebFundConfirmation confirmation, TransactionOrder order) {
    return tradeDifferences(confirmation, order).list().isEmpty();
  }

  private static SebFundConfirmationDifferences tradeDifferences(
      SebFundConfirmation confirmation, TransactionOrder order) {
    return new SebFundConfirmationDifferences()
        .field("fond", confirmation.fund(), order.getFund())
        .field("ISIN", confirmation.isin(), order.getInstrumentIsin())
        .field("suund", confirmation.side(), order.getTransactionType())
        .field("instrumendi liik", FUND, order.getInstrumentType());
  }

  private static @Nullable BigDecimal stripped(@Nullable BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros();
  }

  private record RegisteredTrade(TransactionExecution execution, TransactionOrder order) {}
}
