package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

final class SebPendingReplayMessages {

  private static final Map<SettlementCheck, String> SKIPPED_CHECK_REASONS =
      Map.of(
          SettlementCheck.SKIPPED_EMPTY_REPORT, "tühi raport",
          SettlementCheck.SKIPPED_NO_MATCHED_ROW, "ükski rida ei sobitunud",
          SettlementCheck.SKIPPED_MALFORMED_ROWS, "vigased read",
          SettlementCheck.SKIPPED_NOT_LATEST_REPORT, "polnud viimane raport");
  static final String LOCK_HELD_MESSAGE =
      "SEB ootel-tehingute raportite uuesti sobitamine ei alanud, sest SEB ootel-tehingute"
          + " sobitamine juba käib.\nKäivita uuesti, kui see on lõppenud.";
  private static final String RESUMABLE_JOB = "SebPendingTransactionReconciliationBackfillJob";
  private static final String RERUN_ADDS_NOTHING_TWICE =
      "Uuesti käivitamine ei lisa täitmisi ega arveldusi topelt.";

  private SebPendingReplayMessages() {}

  static Optional<String> skippedCheckReason(SettlementCheck check) {
    return Optional.ofNullable(SKIPPED_CHECK_REASONS.get(check));
  }

  static String windowClosedMessage(String closedHours, String opensAt) {
    return "SEB ootel-tehingute raportite uuesti sobitamine ei alanud, sest kell "
        + closedHours
        + " imporditakse SEB raporteid ja käib igapäevane sobitamine.\nKäivita uuesti pärast kella "
        + opensAt
        + ".";
  }

  static String noReportsMessage(LocalDate from) {
    return "SEB ootel-tehingute raporteid pole alates " + from + ", midagi ei sobitatud uuesti.";
  }

  static String resumeTrigger(LocalDate from) {
    return RESUMABLE_JOB + ":" + from;
  }

  static String stoppedOutsideAReportMessage() {
    return "SEB ootel-tehingute raportite uuesti sobitamine peatus ootamatult (põhjus on logis).\n"
        + "Paranda põhjus ja käivita uuesti. "
        + RERUN_ADDS_NOTHING_TWICE;
  }

  static String completedMessage(ReplaySummary summary) {
    return "SEB ootel-tehingute raportid sobitati uuesti vanimast uusimani. Raporteid: "
        + reportsAndRange(summary.replayed())
        + ".\n"
        + outcome(summary)
        + "Lahknevused ja kärbitud raportid on auditis (investment_transaction_audit_event). "
        + RERUN_ADDS_NOTHING_TWICE;
  }

  static String stoppedMessage(Stop stop, ReplaySummary summary) {
    return "SEB ootel-tehingute raportite uuesti sobitamine peatus "
        + stop.reason().where.formatted(stop.reportDate())
        + (summary.replayed().isEmpty()
            ? "Enne seda ei sobitatud ühtki raportit.\n"
            : "Enne seda raporteid: "
                + reportsAndRange(summary.replayed())
                + ". "
                + outcome(summary))
        + stop.reason().advice.formatted(stop.reportDate())
        + "INSERT INTO investment_job_trigger (job_name) VALUES ('"
        + resumeTrigger(stop.reportDate())
        + "');\n"
        + RERUN_ADDS_NOTHING_TWICE;
  }

  private static String reportsAndRange(List<LocalDate> replayed) {
    return replayed.size() == 1
        ? "1 (" + replayed.getFirst() + ")"
        : replayed.size() + " (" + replayed.getFirst() + " kuni " + replayed.getLast() + ")";
  }

  private static String outcome(ReplaySummary summary) {
    List<ReconciliationAlert> alerts = summary.alerts();
    return "Uusi täitmisi: "
        + summary.newExecutions()
        + ", uusi arveldusi: "
        + summary.newSettlements()
        + ". Saatmata hoiatusi: "
        + alerts.size()
        + " (koguse või summa lahknevusi: "
        + count(alerts, QuantityAmountMismatchEvent.class)
        + ", kärbitud raporteid: "
        + count(alerts, PossibleReportTruncationEvent.class)
        + ", tükkide hinnaerinevusi: "
        + count(alerts, ExecutionPriceConsistencyEvent.class)
        + ").\n"
        + skippedChecksLine(summary.skippedChecks())
        + priceDivergenceLines(alerts);
  }

  private static long count(
      List<ReconciliationAlert> alerts, Class<? extends ReconciliationAlert> kind) {
    return alerts.stream().filter(kind::isInstance).count();
  }

  private static String skippedChecksLine(List<SkippedCheck> skippedChecks) {
    return skippedChecks.isEmpty()
        ? ""
        : skippedChecks.stream()
            .map(skipped -> skipped.reportDate() + " (" + skipped.reason() + ")")
            .collect(joining(", ", "Arvelduse kontroll jäi vahele: ", ".\n"));
  }

  private static String priceDivergenceLines(List<ReconciliationAlert> alerts) {
    return alerts.stream()
        .filter(ExecutionPriceConsistencyEvent.class::isInstance)
        .map(ExecutionPriceConsistencyEvent.class::cast)
        .map(
            divergence ->
                "Tükkide hinnaerinevus: tellimus "
                    + divergence.orderId()
                    + ", "
                    + divergence.isin()
                    + ", raport "
                    + divergence.reportDate()
                    + ".\n")
        .collect(joining());
  }

  record ReplaySummary(
      List<LocalDate> replayed,
      long newExecutions,
      long newSettlements,
      List<ReconciliationAlert> alerts,
      List<SkippedCheck> skippedChecks,
      Optional<Stop> stop) {}

  record SkippedCheck(LocalDate reportDate, String reason) {}

  record Stop(LocalDate reportDate, StopReason reason) {}

  @RequiredArgsConstructor
  enum StopReason {
    REPORT_FAILED(
        "raportil %s (põhjus on logis).\n", "Paranda põhjus ja jätka raportist %s:\n", ERROR),
    IMPORT_WINDOW_OPENS(
        "enne raportit %s, sest algas SEB raportite impordi aeg.\n",
        "Jätka õhtul raportist %s:\n", WARNING),
    TIME_BUDGET_SPENT(
        "enne raportit %s, sest see on kestnud 45 minutit ja käivitajate lukk kehtib 60"
            + " minutit.\n",
        "Jätka raportist %s:\n", WARNING);

    private final String where;
    private final String advice;
    final Severity severity;
  }
}
