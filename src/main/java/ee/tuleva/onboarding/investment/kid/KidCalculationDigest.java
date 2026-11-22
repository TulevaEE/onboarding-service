package ee.tuleva.onboarding.investment.kid;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

record KidCalculationDigest(
    TulevaFund fund,
    int holdingPeriodYears,
    List<StoredKidCalculation> stored,
    Map<Long, List<PrintedScenario>> printedScenarios,
    List<StoredKidCalculation> unpublished,
    List<KidFailedMonth> failedMonths)
    implements KidDigestMessage {

  private static final int FRACTION_TO_PERCENT = 2;
  private static final int PERCENT_SHOWN_WITH_AT_LEAST_DECIMALS = 2;
  private static final String NEXT = "Next: ";
  private static final String FIX_THE_DATA_AND_THE_NEXT_WORKING_DAY_RECALCULATES =
      "kontrolli KID-i hinnaseeriat põhjuses nimetatud kuupäevadel; parandatud andmetega"
          + " arvutab töö kuu järgmisel tööpäeval uuesti";

  @Override
  public String text() {
    return Stream.of(
            Stream.of("%s põhiteabe kuu stsenaariumid".formatted(fund.getCode())),
            stored.stream().map(this::monthLine),
            failedMonths.stream().map(KidCalculationDigest::failedMonthLine),
            awaitingPublication(),
            fixes(),
            failedMonths.stream().map(this::failedMonthFix))
        .flatMap(lines -> lines)
        .collect(joining("\n"));
  }

  @Override
  public Severity severity() {
    return failedMonths.isEmpty() && stored.stream().allMatch(StoredKidCalculation::complete)
        ? INFO
        : WARNING;
  }

  private static String failedMonthLine(KidFailedMonth failed) {
    return "🔴 %s: arvutus ei jooksnud: %s".formatted(failed.month(), failed.failedWith());
  }

  private String failedMonthFix(KidFailedMonth failed) {
    return NEXT
        + "%s: anna IT-le teada; logis on 'KID calculation failed: fund=%s, month=%s'"
            .formatted(failed.month(), fund, failed.month());
  }

  private String monthLine(StoredKidCalculation calculation) {
    if (!calculation.complete()) {
      return "🔴 %s, arvutus %d: %s"
          .formatted(
              calculation.month(),
              calculation.id(),
              String.join("; ", calculation.refusalReasons()));
    }
    var ocf = requireNonNull(calculation.ocf(), "A complete KID calculation names its OCF");
    return "✅ %s, arvutus %d: jooksvad tasud %s; %s"
        .formatted(
            calculation.month(),
            calculation.id(),
            percentInFull(ocf.ongoingCharges()),
            holdingPeriodAmounts(calculation.id()));
  }

  private String holdingPeriodAmounts(long calculationId) {
    var scenarios =
        printedScenarios.getOrDefault(calculationId, List.of()).stream()
            .filter(scenario -> scenario.horizonYears() == holdingPeriodYears)
            .toList();
    if (scenarios.isEmpty()) {
      throw new IllegalStateException(
          "No printed scenarios for the holding period: calculationId=%d, holdingPeriodYears=%d"
              .formatted(calculationId, holdingPeriodYears));
    }
    return "%d a: %s"
        .formatted(
            holdingPeriodYears,
            scenarios.stream()
                .map(
                    scenario ->
                        "%s %s €"
                            .formatted(
                                scenario.scenario().asTheKidNamesIt(),
                                scenario.amountEur().toPlainString()))
                .collect(joining(", ")));
  }

  private Stream<String> awaitingPublication() {
    if (unpublished.isEmpty()) {
      return Stream.empty();
    }
    return Stream.of(
        "Avaldamata valmis kuud: "
            + unpublished.stream()
                .map(
                    calculation ->
                        "%s (arvutus %d)".formatted(calculation.month(), calculation.id()))
                .collect(joining(", ")),
        NEXT
            + "kontrolli ja avalda: agent-gateway `kid.publish` KID_FUND=%s CALCULATION_IDS=%s"
                .formatted(
                    fund.getCode(),
                    unpublished.stream()
                        .map(calculation -> String.valueOf(calculation.id()))
                        .collect(joining(","))));
  }

  private Stream<String> fixes() {
    return stored.stream()
        .flatMap(
            calculation ->
                calculation.refusalReasons().stream()
                    .map(KidCalculationDigest::fix)
                    .distinct()
                    .map(fix -> Map.entry(fix, calculation.month())))
        .collect(
            groupingBy(
                Map.Entry::getKey, LinkedHashMap::new, mapping(Map.Entry::getValue, toList())))
        .entrySet()
        .stream()
        .map(
            monthsByFix ->
                NEXT
                    + monthsByFix.getValue().stream()
                        .map(YearMonth::toString)
                        .collect(joining(", "))
                    + ": "
                    + monthsByFix.getKey());
  }

  private static String fix(String reason) {
    return KidRefusalCause.of(reason)
        .map(
            cause ->
                switch (cause) {
                  case NO_OCF_IN_FORCE ->
                      "kanna KID-i jooksvate tasude näitaja agent-gateway `ocf.publish_given`"
                          + " kaudu";
                  case SERIES_STOPPED, NO_PRICES -> "kontrolli KID-i hinnaseeria voogu";
                })
        .orElse(FIX_THE_DATA_AND_THE_NEXT_WORKING_DAY_RECALCULATES);
  }

  private static String percentInFull(BigDecimal fraction) {
    var percent = fraction.movePointRight(FRACTION_TO_PERCENT).stripTrailingZeros();
    var shown =
        percent.scale() < PERCENT_SHOWN_WITH_AT_LEAST_DECIMALS
            ? percent.setScale(PERCENT_SHOWN_WITH_AT_LEAST_DECIMALS)
            : percent;
    return shown.toPlainString().replace('.', ',') + "%";
  }
}
