package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.InstrumentType.FUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebReportedPrecision.asSebReportsIt;
import static ee.tuleva.onboarding.investment.transaction.ingest.TransactionExecutionMapper.SOURCE_SEB_OOTEL;

import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import ee.tuleva.onboarding.investment.transaction.TransactionExecutionRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionOrder;
import ee.tuleva.onboarding.investment.transaction.TransactionOrderRepository;
import ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.RegistryComparison;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SebFundConfirmationRegistryCheck {

  private final TransactionExecutionRepository executionRepository;
  private final TransactionOrderRepository orderRepository;
  private final ReportedQuantityNormalizer quantityNormalizer;

  RegistryComparison compare(SebFundConfirmation confirmation) {
    return registered(confirmation)
        .map(
            trade ->
                RegistryComparison.of(
                    figureDifferences(
                            tradeDifferences(confirmation, trade.order()), confirmation, trade)
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

  private SebFundConfirmationDifferences figureDifferences(
      SebFundConfirmationDifferences differences,
      SebFundConfirmation confirmation,
      RegisteredTrade trade) {
    TransactionExecution execution = trade.execution();
    return differences
        .storedAmount(
            "kogus",
            confirmation.quantity(),
            quantitiesItsWriterStores(confirmation, trade),
            execution.getExecutedQuantity())
        .storedAmount(
            "hind",
            confirmation.price(),
            confirmedOrAsSebReportsIt(confirmation.price()),
            execution.getUnitPrice())
        .storedAmount(
            "kogumaksumus",
            confirmation.totalAmount(),
            confirmedOrAsSebReportsIt(confirmation.totalAmount()),
            execution.getTotalConsideration());
  }

  private List<BigDecimal> quantitiesItsWriterStores(
      SebFundConfirmation confirmation, RegisteredTrade trade) {
    BigDecimal confirmed = confirmation.quantity().abs();
    BigDecimal reported = asSebReportsIt(confirmed);
    BigDecimal normalized = normalized(reported, confirmation, trade.order());
    return isFromSebsReport(trade.execution())
        ? List.of(confirmed, normalized)
        : List.of(confirmed, reported, normalized);
  }

  private BigDecimal normalized(
      BigDecimal reported, SebFundConfirmation confirmation, TransactionOrder order) {
    return quantityNormalizer.storedQuantity(
        order,
        reported,
        executionRepository.findAllByOrderId(order.getId()),
        confirmation.tradeReference());
  }

  private static List<BigDecimal> confirmedOrAsSebReportsIt(BigDecimal confirmed) {
    return List.of(confirmed, asSebReportsIt(confirmed));
  }

  private static boolean isFromSebsReport(TransactionExecution execution) {
    return SOURCE_SEB_OOTEL.equals(execution.getSource());
  }

  private record RegisteredTrade(TransactionExecution execution, TransactionOrder order) {}
}
