package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.investment.transaction.TransactionAuditEvent;
import ee.tuleva.onboarding.investment.transaction.TransactionAuditEventRepository;
import ee.tuleva.onboarding.investment.transaction.TransactionExecution;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebFundConfirmationAuditRecorder {

  static final String VERIFIED = "SEB_FUND_CONFIRMATION_VERIFIED";
  static final String ALERTED = "SEB_FUND_CONFIRMATION_ALERTED";
  static final String MISSING_ALERTED = "SEB_FUND_CONFIRMATION_MISSING_ALERTED";
  static final String UNREADABLE_ALERTED = "SEB_FUND_CONFIRMATION_UNREADABLE_ALERTED";
  static final String MISNAMED_ALERTED = "SEB_FUND_CONFIRMATION_MISNAMED_ALERTED";
  static final String CHECK_FAILURE_ALERTED = "SEB_FUND_CONFIRMATION_CHECK_FAILURE_ALERTED";
  static final String NAV_DATE_SET = "SEB_FUND_CONFIRMATION_NAV_DATE_SET";
  private static final String SYSTEM_ACTOR = "system";
  private static final String STATUS = "status";
  private static final String FINDINGS = "findings";

  private final TransactionAuditEventRepository auditEventRepository;
  private final Clock clock;

  void recordVerified(SebFundConfirmationOutcome outcome, String actor) {
    String reference = outcome.confirmation().tradeReference();
    Map<String, Object> payload = payload(outcome);
    if (lastVerifiedPayload(reference).filter(payload::equals).isPresent()) {
      return;
    }
    save(VERIFIED, reference, actor, payload);
  }

  boolean wasVerified(String reference) {
    return !auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, reference).isEmpty();
  }

  List<String> unalertedFindings(SebFundConfirmationOutcome outcome) {
    SebFundConfirmation confirmation = outcome.confirmation();
    String reference = confirmation.tradeReference();
    Set<String> alerted;
    try {
      alerted =
          auditEventRepository.findByEventTypeAndDedupKey(ALERTED, reference).stream()
              .map(TransactionAuditEvent::getPayload)
              .filter(payload -> isAboutTheSameFigures(payload, confirmation))
              .map(payload -> payload.get(FINDINGS))
              .filter(List.class::isInstance)
              .flatMap(findings -> ((List<?>) findings).stream())
              .map(String::valueOf)
              .collect(toSet());
    } catch (RuntimeException e) {
      logUnreadableAlertHistory(reference, e);
      return outcome.result().findings();
    }
    return outcome.result().findings().stream()
        .filter(finding -> !alerted.contains(finding))
        .toList();
  }

  void recordAlerted(SebFundConfirmationOutcome outcome, List<String> findings) {
    Map<String, Object> payload = payload(outcome);
    payload.put(FINDINGS, List.copyOf(findings));
    save(ALERTED, outcome.confirmation().tradeReference(), SYSTEM_ACTOR, payload);
  }

  boolean alreadyAlerted(MissingSebFundConfirmation missing) {
    return hasAlerted(MISSING_ALERTED, missing.tradeReference());
  }

  void recordAlerted(MissingSebFundConfirmation missing) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", missing.fund().name());
    payload.put("isin", missing.isin());
    payload.put("side", missing.side().name());
    payload.put("tradeDate", missing.tradeDate().toString());
    payload.put("quantity", missing.quantity().toPlainString());
    save(MISSING_ALERTED, missing.tradeReference(), SYSTEM_ACTOR, payload);
  }

  boolean alreadyAlerted(UnreadableSebFundConfirmation unreadable) {
    return hasAlerted(UNREADABLE_ALERTED, unreadable.key());
  }

  void recordNavDateSet(
      SebFundConfirmation confirmation,
      TransactionExecution execution,
      @Nullable LocalDate replacedNavDate) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("executionId", String.valueOf(execution.getId()));
    payload.put("navDate", confirmation.pricingDate().toString());
    if (replacedNavDate != null) {
      payload.put("previousNavDate", replacedNavDate.toString());
    }
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(NAV_DATE_SET)
            .orderId(execution.getOrderId())
            .actor(SYSTEM_ACTOR)
            .dedupKey(confirmation.tradeReference())
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }

  boolean alreadyAlertedToday(SebFundConfirmationCheckFailure failure) {
    return hasAlerted(CHECK_FAILURE_ALERTED, todaysKey(failure));
  }

  void recordAlerted(SebFundConfirmationCheckFailure failure) {
    save(
        CHECK_FAILURE_ALERTED,
        todaysKey(failure),
        SYSTEM_ACTOR,
        Map.of("step", failure.step(), "reason", failure.reason()));
  }

  private String todaysKey(SebFundConfirmationCheckFailure failure) {
    return LocalDate.ofInstant(clock.instant(), SEB_TIME_ZONE) + " " + failure.step();
  }

  private boolean hasAlerted(String eventType, String dedupKey) {
    try {
      return !auditEventRepository.findByEventTypeAndDedupKey(eventType, dedupKey).isEmpty();
    } catch (RuntimeException e) {
      logUnreadableAlertHistory(dedupKey, e);
      return false;
    }
  }

  private static void logUnreadableAlertHistory(String dedupKey, RuntimeException e) {
    log.error(
        "SEB fund confirmation alert history unreadable, alerting again: dedupKey={}, error={}",
        dedupKey,
        e.getMessage(),
        e);
  }

  void recordAlerted(UnreadableSebFundConfirmation unreadable) {
    save(UNREADABLE_ALERTED, unreadable.key(), SYSTEM_ACTOR, Map.of("reason", unreadable.reason()));
  }

  boolean alreadyAlerted(MisnamedSebFundConfirmation misnamed) {
    return hasAlerted(MISNAMED_ALERTED, misnamed.key());
  }

  void recordAlerted(MisnamedSebFundConfirmation misnamed) {
    save(
        MISNAMED_ALERTED,
        misnamed.key(),
        SYSTEM_ACTOR,
        Map.of(
            "nameReference", misnamed.nameReference(),
            "contentReference", misnamed.contentReference()));
  }

  private Optional<Map<String, Object>> lastVerifiedPayload(String reference) {
    return auditEventRepository.findByEventTypeAndDedupKey(VERIFIED, reference).stream()
        .max(comparing(TransactionAuditEvent::getCreatedAt))
        .map(TransactionAuditEvent::getPayload);
  }

  private void save(String eventType, String dedupKey, String actor, Map<String, Object> payload) {
    auditEventRepository.save(
        TransactionAuditEvent.builder()
            .eventType(eventType)
            .actor(actor)
            .dedupKey(dedupKey)
            .payload(payload)
            .createdAt(clock.instant())
            .build());
  }

  private static boolean isAboutTheSameFigures(
      Map<String, Object> payload, SebFundConfirmation confirmation) {
    return figures(confirmation).entrySet().stream()
        .allMatch(figure -> figure.getValue().equals(payload.get(figure.getKey())));
  }

  private static Map<String, Object> figures(SebFundConfirmation confirmation) {
    Map<String, Object> figures = new LinkedHashMap<>();
    figures.put("pricingDate", confirmation.pricingDate().toString());
    figures.put("valueDate", confirmation.valueDate().toString());
    figures.put("quantity", confirmation.quantity().toPlainString());
    figures.put("price", confirmation.price().toPlainString());
    figures.put("tradeAmount", confirmation.tradeAmount().toPlainString());
    figures.put("fee", confirmation.fee().toPlainString());
    figures.put("totalAmount", confirmation.totalAmount().toPlainString());
    return figures;
  }

  private static Map<String, Object> payload(SebFundConfirmationOutcome outcome) {
    SebFundConfirmation confirmation = outcome.confirmation();
    SebFundConfirmationResult result = outcome.result();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("fund", confirmation.fund().name());
    payload.put("isin", confirmation.isin());
    payload.put("side", confirmation.side().name());
    payload.put("orderReceivedAt", confirmation.orderReceivedAt().toString());
    payload.putAll(figures(confirmation));
    payload.put(STATUS, result.statusSignature().toString());
    payload.put("reportDifferences", List.copyOf(result.reportDifferences()));
    payload.put("registryStatus", result.registry().status().name());
    payload.put("registryDifferences", List.copyOf(result.registry().differences()));
    putIfPresent(payload, "nav", result.nav());
    putIfPresent(payload, "deviationPercent", result.deviationPercent());
    payload.put("navDatesAfterReceipt", result.navDatesAfterReceipt());
    return payload;
  }

  private static void putIfPresent(
      Map<String, Object> payload, String key, @Nullable BigDecimal value) {
    if (value != null) {
      payload.put(key, value.toPlainString());
    }
  }
}
