package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.Comparator.comparing;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class CostBasisOversellAlerter {

  static final String OVERSELL_ALERTED = "COST_BASIS_OVERSELL_ALERTED";
  private static final String SYSTEM_ACTOR = "system";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private final TransactionAuditEventRepository auditEventRepository;
  private final OperationsNotificationService notificationService;
  private final Clock clock;

  void alert(TulevaFund fund, CostBasisOversellException oversell) {
    String dedupKey = dedupKey(fund, oversell);
    Map<String, Object> payload = payload(fund, oversell);
    if (alreadyAlerted(dedupKey, payload) || !send(message(fund, oversell), dedupKey)) {
      return;
    }
    record(dedupKey, payload);
  }

  private boolean alreadyAlerted(String dedupKey, Map<String, Object> payload) {
    try {
      return auditEventRepository.findByEventTypeAndDedupKey(OVERSELL_ALERTED, dedupKey).stream()
          .max(
              comparing(TransactionAuditEvent::getCreatedAt)
                  .thenComparing(TransactionAuditEvent::getId))
          .map(TransactionAuditEvent::getPayload)
          .filter(payload::equals)
          .isPresent();
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell alert history unreadable, alerting again: dedupKey={}, error={}",
          dedupKey,
          e.getMessage(),
          e);
      return false;
    }
  }

  private boolean send(String message, String dedupKey) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Failed to send cost-basis oversell alert to Slack, next run retries: dedupKey={},"
              + " error={}",
          dedupKey,
          e.getMessage(),
          e);
      return false;
    }
  }

  private void record(String dedupKey, Map<String, Object> payload) {
    try {
      auditEventRepository.save(
          TransactionAuditEvent.builder()
              .eventType(OVERSELL_ALERTED)
              .dedupKey(dedupKey)
              .actor(SYSTEM_ACTOR)
              .payload(payload)
              .createdAt(clock.instant())
              .build());
    } catch (RuntimeException e) {
      log.error(
          "Cost-basis oversell alert sent but not recorded, it may repeat: dedupKey={}, error={}",
          dedupKey,
          e.getMessage(),
          e);
    }
  }

  private static String dedupKey(TulevaFund fund, CostBasisOversellException oversell) {
    return fund.getCode() + " " + oversell.getInstrumentIsin() + " " + oversell.getAsOfDate();
  }

  private static Map<String, Object> payload(TulevaFund fund, CostBasisOversellException oversell) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", fund.getCode());
    payload.put("instrumentIsin", oversell.getInstrumentIsin());
    payload.put("asOfDate", oversell.getAsOfDate().toString());
    payload.put("heldQuantity", plain(oversell.getHeldQuantity()));
    payload.put("sellQuantity", plain(oversell.getSellQuantity()));
    return payload;
  }

  private static String message(TulevaFund fund, CostBasisOversellException oversell) {
    return String.join(
        "\n",
        "🔴 "
            + fund.getCode()
            + " soetusmaksumus "
            + DATE.format(oversell.getAsOfDate())
            + ": "
            + oversell.getInstrumentIsin()
            + " müük "
            + plain(oversell.getSellQuantity())
            + " ühikut, teadaolev positsioon "
            + plain(oversell.getHeldQuantity()),
        "Fondi soetusmaksumus ei sisalda seda müüki, kuni see on parandamata",
        "Leia puuduv ost või vale müügikogus tehinguregistris või algpositsioonis ja paranda see");
  }

  private static String plain(BigDecimal quantity) {
    return quantity.stripTrailingZeros().toPlainString();
  }
}
