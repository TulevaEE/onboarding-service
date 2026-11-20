package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

record RebalanceMessage(String text, Severity severity) {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  static RebalanceMessage of(RebalanceWindow window) {
    return switch (window.state()) {
      case INFO -> opened(window);
      case WARNING -> deadlineApproaching(window);
      case FAIL -> deadlineMissed(window);
      case OK -> closed(window, requireNonNull(window.closure()));
    };
  }

  static RebalanceMessage ofSuperseded(SupersededWindow superseded) {
    var window = superseded.window();
    var closure = window.closure();
    return closure != null
        ? closed(window, closure)
        : replacedBeforeComingIntoLine(window, superseded.replacedOn());
  }

  static RebalanceMessage couldNotRun(TulevaFund fund) {
    return new RebalanceMessage(
        ("⏸ %s mudelportfelli tasakaalustamise kontroll ei jooksnud; põhjus on rakenduse logis,"
                + " kontroll proovib järgmisel tööpäeval uuesti")
            .formatted(fund.getCode()),
        ERROR);
  }

  private static RebalanceMessage opened(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        ℹ️ %s: tähtaeg %s (sisekord 4 p 2.6.1), jäänud %d päeva
        %s"""
            .formatted(
                model(window),
                date(window.deadline()),
                window.daysUntilDeadline(),
                measurement(window)),
        INFO);
  }

  private static RebalanceMessage deadlineApproaching(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        ⚠️ %s: tähtaeg %s, jäänud %d päeva; Rebalance-jooksu tehinguid pole
        %s
        Tee tasakaalustamise tehingud või vii fond tähtajaks %s pp piiresse"""
            .formatted(
                model(window),
                date(window.deadline()),
                window.daysUntilDeadline(),
                measurement(window),
                points(window.tolerance())),
        WARNING);
  }

  private static RebalanceMessage deadlineMissed(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        🛑 %s: tähtaeg oli %s, fond pole mudeliga vastavuses ja Rebalance-jooksu tehinguid pole
        %s
        Vii fond %s pp piiresse (sisekord 4 p 2.6.1)"""
            .formatted(
                model(window),
                date(window.deadline()),
                measurement(window),
                points(window.tolerance())),
        ERROR);
  }

  private static RebalanceMessage closed(RebalanceWindow window, RebalanceClosure closure) {
    return new RebalanceMessage(
        switch (closure.by()) {
          case AN_IN_LINE_NAV ->
              "✅ %s: fond on mudeliga vastavuses (kõik osakaalud %s pp piires, NAV %s), kontroll suletud"
                  .formatted(model(window), points(window.tolerance()), date(closure.on()));
          case REBALANCING_TRADES ->
              "✅ %s: tasakaalustamise tehingud tehtud %s, kontroll suletud"
                  .formatted(model(window), date(closure.on()));
        },
        INFO);
  }

  private static RebalanceMessage replacedBeforeComingIntoLine(
      RebalanceWindow window, LocalDate replacedOn) {
    return new RebalanceMessage(
        "⏹ %s: asendatud mudeliga alates %s, vastavusse ei jõudnud; kontroll suletud"
            .formatted(model(window), date(replacedOn)),
        INFO);
  }

  private static String model(RebalanceWindow window) {
    return "%s mudelportfell alates %s"
        .formatted(window.fund().getCode(), date(window.modelEffectiveDate()));
  }

  private static String measurement(RebalanceWindow window) {
    var latestNavDate = window.latestNavDate();
    if (latestNavDate == null) {
      return "NAV alates %s pole veel avaldatud".formatted(date(window.modelEffectiveDate()));
    }
    return "Üle %s pp (NAV %s): %s"
        .formatted(
            points(window.tolerance()),
            date(latestNavDate),
            window.outsideToleranceAtLatestNav().stream()
                .map(
                    deviation ->
                        deviation.isin() + " " + signedPoints(deviation.percentagePoints()) + " pp")
                .collect(joining(", ")));
  }

  private static String points(BigDecimal weight) {
    return weight.movePointRight(2).stripTrailingZeros().toPlainString().replace('.', ',');
  }

  private static String signedPoints(BigDecimal percentagePoints) {
    return String.format(Locale.ROOT, "%+.2f", percentagePoints).replace('.', ',');
  }

  private static String date(LocalDate date) {
    return date.format(DATE);
  }
}
