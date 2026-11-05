package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;
import static ee.tuleva.onboarding.investment.transaction.SettlementSource.BANK_STATEMENT;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSettlementService {

  private final TransactionSettlementRepository settlementRepository;
  private final TransactionOrderRepository orderRepository;
  private final Clock clock;

  @Transactional
  public TransactionSettlement recordSettlement(
      TransactionOrder order, LocalDate reportDate, SettlementSource source) {
    Optional<TransactionSettlement> existing = settlementRepository.findByOrderId(order.getId());
    if (existing.isPresent()) {
      log.warn(
          "Settlement already recorded, skipping: orderId={}, existingSettlementId={}",
          order.getId(),
          existing.get().getId());
      return existing.get();
    }

    TransactionSettlement settlement =
        settlementRepository.save(
            TransactionSettlement.builder()
                .orderId(order.getId())
                .settledAt(clock.instant())
                .reportDate(reportDate)
                .source(source)
                .build());

    order.setOrderStatus(SETTLED);
    orderRepository.save(order);

    log.info(
        "Recorded settlement: orderId={}, reportDate={}, source={}, settlementId={}",
        order.getId(),
        reportDate,
        source,
        settlement.getId());
    return settlement;
  }

  @Transactional(readOnly = true)
  public boolean isSettledByTheBank(TransactionOrder order) {
    return settlementRepository
        .findByOrderId(order.getId())
        .filter(settlement -> settlement.getSource() == BANK_STATEMENT)
        .isPresent();
  }

  @Transactional
  public BankDating settleFromTheBank(TransactionOrder order, LocalDate bookingDate) {
    Optional<TransactionSettlement> existing = settlementRepository.findByOrderId(order.getId());
    if (existing.isEmpty()) {
      TransactionSettlement recorded = recordSettlement(order, bookingDate, BANK_STATEMENT);
      return recorded.getSource() == BANK_STATEMENT
          ? new BankDating.Settled()
          : redate(order, recorded, bookingDate);
    }
    if (existing.get().getSource() == BANK_STATEMENT) {
      return new BankDating.AlreadyByTheBank();
    }
    return redate(order, existing.get(), bookingDate);
  }

  private BankDating redate(
      TransactionOrder order, TransactionSettlement settlement, LocalDate bookingDate) {
    PreviousSettlement previous =
        new PreviousSettlement(settlement.getReportDate(), settlement.getSource());
    settlement.setReportDate(bookingDate);
    settlement.setSource(BANK_STATEMENT);
    settlementRepository.save(settlement);
    log.info(
        "Settlement dated by the bank: orderId={}, bookingDate={}, previousDate={},"
            + " previousSource={}",
        order.getId(),
        bookingDate,
        previous.reportDate(),
        previous.source());
    return new BankDating.Redated(previous);
  }
}
