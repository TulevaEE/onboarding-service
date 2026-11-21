package ee.tuleva.onboarding.investment.check.rebalance;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.INFO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

record RebalanceMessage(String text, Severity severity) {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final String NO_REBALANCE_RUN_TRADES = "Rebalance-jooksu tehinguid pole";
  private static final String CHECK_THE_EXECUTION_WITH_SEB = "Kontrolli SEB-st tehingute täitmist";

  static RebalanceMessage of(RebalanceWindow window) {
    return switch (window.state()) {
      case INFO -> opened(window);
      case WARNING -> deadlineApproaching(window);
      case AWAITING_EXECUTION -> awaitingExecutionPastTheDeadline(window);
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
        ⚠️ %s: tähtaeg %s, jäänud %d päeva; %s
        %s
        %s"""
            .formatted(
                model(window),
                date(window.deadline()),
                window.daysUntilDeadline(),
                rebalanceRunTrades(window),
                measurement(window),
                actionBeforeTheDeadline(window)),
        WARNING);
  }

  private static RebalanceMessage awaitingExecutionPastTheDeadline(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        ⚠️ %s: tähtaeg oli %s, fond pole mudeliga vastavuses; %s
        %s
        %s (sisekord 4 p 2.6.1)"""
            .formatted(
                model(window),
                date(window.deadline()),
                rebalanceRunTrades(window),
                measurement(window),
                CHECK_THE_EXECUTION_WITH_SEB),
        WARNING);
  }

  private static RebalanceMessage deadlineMissed(RebalanceWindow window) {
    return new RebalanceMessage(
        """
        🛑 %s: tähtaeg oli %s, fond pole mudeliga vastavuses ja %s
        %s
        Vii fond %s pp piiresse (sisekord 4 p 2.6.1)"""
            .formatted(
                model(window),
                date(window.deadline()),
                rebalanceRunTrades(window),
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

  private static String rebalanceRunTrades(RebalanceWindow window) {
    var phrases =
        Stream.of(
                awaitingConfirmation(window.ordersAwaitingConfirmation()),
                overdueForConfirmation(window.ordersOverdueForConfirmation()))
            .flatMap(Optional::stream)
            .toList();
    return phrases.isEmpty() ? NO_REBALANCE_RUN_TRADES : String.join("; ", phrases);
  }

  private static Optional<String> awaitingConfirmation(List<SentRebalanceOrder> orders) {
    return orders.stream()
        .map(SentRebalanceOrder::sentOn)
        .min(naturalOrder())
        .map(
            firstSentOn ->
                "Rebalance-jooksu tehingud saadetud %s, ootavad SEB täitmise kinnitust"
                    .formatted(date(firstSentOn)));
  }

  private static Optional<String> overdueForConfirmation(List<SentRebalanceOrder> orders) {
    if (orders.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        "SEB pole Rebalance-jooksu tehingute täitmist kinnitanud: "
            + orders.stream()
                .sorted(
                    comparing(SentRebalanceOrder::sentOn).thenComparing(SentRebalanceOrder::isin))
                .map(order -> "%s (saadetud %s)".formatted(order.isin(), date(order.sentOn())))
                .distinct()
                .collect(joining(", ")));
  }

  private static String actionBeforeTheDeadline(RebalanceWindow window) {
    var makeTheTrades =
        "Tee tasakaalustamise tehingud või vii fond tähtajaks %s pp piiresse"
            .formatted(points(window.tolerance()));
    if (!window.ordersOverdueForConfirmation().isEmpty()) {
      return makeTheTrades + "; kontrolli SEB-st saadetud tehingute täitmist";
    }
    return window.ordersAwaitingConfirmation().isEmpty()
        ? makeTheTrades
        : CHECK_THE_EXECUTION_WITH_SEB;
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
