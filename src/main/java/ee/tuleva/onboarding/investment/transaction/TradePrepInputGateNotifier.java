package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TradePrepInputGateNotifier {

  private final OperationsNotificationService notificationService;

  void notifyStopped(List<StoppedByInputGates> stops) {
    stops.stream()
        .collect(groupingBy(StoppedByInputGates::asOfDate, LinkedHashMap::new, toList()))
        .forEach(this::send);
  }

  private void send(LocalDate asOfDate, List<StoppedByInputGates> stops) {
    try {
      notificationService.sendMessage(message(asOfDate, stops), INVESTMENT);
    } catch (RuntimeException e) {
      log.error(
          "Trade prep input gate notification failed: asOfDate={}, funds={}",
          asOfDate,
          fundsOf(stops),
          e);
    }
  }

  private static String message(LocalDate asOfDate, List<StoppedByInputGates> stops) {
    List<TulevaFund> stoppedFunds = fundsOf(stops);
    return Stream.concat(
            Stream.of(header(asOfDate, stoppedFunds)),
            fundsByLine(stops).entrySet().stream()
                .map(line -> withFundsUnlessAllShareIt(line, stoppedFunds)))
        .collect(joining("\n"));
  }

  private static String header(LocalDate asOfDate, List<TulevaFund> stoppedFunds) {
    if (stoppedFunds.size() == 1) {
      return "🔴 %s tehingute mustandit ei tehtud (%s). Käivita ettevalmistus uuesti, kui sisend on korras:"
          .formatted(stoppedFunds.getFirst().getCode(), asOfDate);
    }
    return "🔴 Tehingute mustandit ei tehtud (%s): %s. Käivita ettevalmistus uuesti, kui sisend on korras:"
        .formatted(asOfDate, codes(stoppedFunds));
  }

  private static Map<String, List<TulevaFund>> fundsByLine(List<StoppedByInputGates> stops) {
    Map<String, List<TulevaFund>> fundsByLine = new LinkedHashMap<>();
    stops.forEach(
        stop ->
            stop.failures()
                .forEach(
                    failure ->
                        fundsByLine
                            .computeIfAbsent(failure.slackLine(), line -> new ArrayList<>())
                            .add(stop.fund())));
    return fundsByLine;
  }

  private static String withFundsUnlessAllShareIt(
      Map.Entry<String, List<TulevaFund>> line, List<TulevaFund> stoppedFunds) {
    List<TulevaFund> lineFunds = line.getValue().stream().distinct().toList();
    if (lineFunds.size() == stoppedFunds.size()) {
      return line.getKey();
    }
    return codes(lineFunds) + ": " + line.getKey();
  }

  private static List<TulevaFund> fundsOf(List<StoppedByInputGates> stops) {
    return stops.stream().map(StoppedByInputGates::fund).distinct().toList();
  }

  private static String codes(List<TulevaFund> funds) {
    return funds.stream().map(TulevaFund::getCode).collect(joining(", "));
  }
}
