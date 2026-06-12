package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.OrderStatus.SETTLED;

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
  public TransactionSettlement recordSettlement(TransactionOrder order, LocalDate reportDate) {
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
                .build());

    order.setOrderStatus(SETTLED);
    orderRepository.save(order);

    log.info(
        "Recorded settlement: orderId={}, reportDate={}, settlementId={}",
        order.getId(),
        reportDate,
        settlement.getId());
    return settlement;
  }
}
