package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Failed;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Resolved;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class InstrumentFeeRateNotifier {

  private static final String LINE_INDENT = "  ";
  private static final String HOW_TO_RESOLVE_AGAIN =
      "Once an agreement is fixed, INSERT INTO investment_job_trigger (job_name) VALUES"
          + " ('InstrumentFeeRateJob') resolves every closed month again; the newest rates are the"
          + " ones read.";

  private final OperationsNotificationService notificationService;

  void announce(List<MonthResolution> resolutions) {
    var needingALook = resolutions.stream().filter(InstrumentFeeRateNotifier::needsALook).toList();
    if (needingALook.isEmpty()) {
      log.info(
          "Instrument fee rates resolved from their agreements: months={}", months(resolutions));
      return;
    }
    notificationService.sendMessage(
        Stream.of(
                Stream.of(
                    "❌ Instrument fee rates need a look before the OCF and the TD attribution"
                        + " read them: "
                        + months(needingALook)),
                needingALook.stream().flatMap(InstrumentFeeRateNotifier::lines),
                Stream.of(HOW_TO_RESOLVE_AGAIN))
            .flatMap(lines -> lines)
            .collect(joining("\n")),
        INVESTMENT,
        ERROR);
  }

  private static boolean needsALook(MonthResolution resolution) {
    return lines(resolution).findAny().isPresent();
  }

  private static Stream<String> lines(MonthResolution resolution) {
    return switch (resolution) {
      case Failed failed ->
          Stream.of(
              LINE_INDENT
                  + "%s could not be resolved: %s".formatted(failed.month(), failed.reason()));
      case Resolved resolved ->
          Stream.concat(
              resolved.rates().stream()
                  .filter(InstrumentRate::fellBackToThePublishedOcf)
                  .map(
                      rate ->
                          LINE_INDENT
                              + "%s %s fell back to the published OCF: %s"
                                  .formatted(resolved.month(), rate.isin(), rate.fallbackReason())),
              resolved.rates().stream()
                  .filter(InstrumentRate::agreedNetAboveThePublishedOcf)
                  .map(
                      rate ->
                          LINE_INDENT
                              + ("%s %s agreed net OCF is above the published OCF; the fund bears"
                                      + " the agreed net, check the agreement")
                                  .formatted(resolved.month(), rate.isin())));
    };
  }

  private static String months(List<MonthResolution> resolutions) {
    return resolutions.stream()
        .map(MonthResolution::month)
        .map(Object::toString)
        .distinct()
        .collect(joining(", "));
  }
}
