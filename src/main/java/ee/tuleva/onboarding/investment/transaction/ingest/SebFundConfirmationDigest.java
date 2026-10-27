package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.NOT_PUBLISHED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.LATE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class SebFundConfirmationDigest {

  private static final String HEADER = "SEB fonditehingute kinnitused";
  private static final String BLOCK_SEPARATOR = "\n\n";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final OperationsNotificationService notificationService;
  private final SebFundConfirmationAuditRecorder auditRecorder;
  private final SebFundConfirmationProperties properties;

  void publish(
      List<SebFundConfirmationOutcome> outcomes,
      List<MissingSebFundConfirmation> missing,
      List<UnreadableSebFundConfirmation> unreadable,
      List<SebFundConfirmationCheckFailure> failures) {
    List<NewFindings> newFindings =
        outcomes.stream()
            .filter(outcome -> outcome.result().isActionable())
            .map(outcome -> new NewFindings(outcome, auditRecorder.unalertedFindings(outcome)))
            .filter(found -> !found.findings().isEmpty())
            .toList();
    List<MissingSebFundConfirmation> newMissing =
        missing.stream().filter(trade -> !auditRecorder.alreadyAlerted(trade)).toList();
    List<UnreadableSebFundConfirmation> newUnreadable =
        unreadable.stream().filter(file -> !auditRecorder.alreadyAlerted(file)).toList();

    List<String> blocks = new ArrayList<>();
    newFindings.forEach(found -> blocks.addAll(blocks(found.outcome(), found.findings())));
    newMissing.forEach(trade -> blocks.add(block(trade)));
    newUnreadable.forEach(file -> blocks.add(block(file)));
    List<SebFundConfirmationCheckFailure> newFailures =
        failures.stream().filter(failure -> !auditRecorder.alreadyAlertedToday(failure)).toList();
    newFailures.forEach(failure -> blocks.add(block(failure)));
    if (blocks.isEmpty()
        || !send(HEADER + BLOCK_SEPARATOR + String.join(BLOCK_SEPARATOR, blocks))) {
      return;
    }
    try {
      newFindings.forEach(found -> auditRecorder.recordAlerted(found.outcome(), found.findings()));
      newMissing.forEach(auditRecorder::recordAlerted);
      newUnreadable.forEach(auditRecorder::recordAlerted);
      newFailures.forEach(auditRecorder::recordAlerted);
    } catch (RuntimeException e) {
      log.error(
          "SEB fund confirmation alerts sent but not recorded, they may repeat: error={}",
          e.getMessage(),
          e);
    }
  }

  private List<String> blocks(SebFundConfirmationOutcome outcome, List<String> findings) {
    SebFundConfirmation confirmation = outcome.confirmation();
    SebFundConfirmationResult result = outcome.result();
    List<String> blocks = new ArrayList<>();
    if (findings.contains(result.mismatchFinding())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + " "
                  + date(confirmation.pricingDate())
                  + ": SEB tehingukinnitus ei klapi ootel tehingute raportiga ("
                  + String.join("; ", result.reportDifferences())
                  + ")",
              received(confirmation),
              "Võrdle kinnitust ja raportit, vajadusel küsi SEB-lt parandust"));
    }
    if (findings.contains(NOT_FOUND.name())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + " "
                  + date(confirmation.pricingDate())
                  + ": tehingut pole SEB ootel tehingute raportis",
              "Tehing "
                  + confirmation.tradeReference()
                  + ", väärtuspäev "
                  + date(confirmation.valueDate()),
              "Küsi SEB-lt, kas tehing on registreeritud"));
    }
    if (findings.contains(FAR_FROM_NAV.name())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + " "
                  + date(confirmation.pricingDate())
                  + ": hind "
                  + confirmation.price().toPlainString()
                  + " erineb fondi NAV-ist "
                  + plain(result.nav())
                  + " "
                  + signedPercent(result.deviationPercent()),
              received(confirmation),
              "Kontrolli hinda SEB-lt ja fondivalitseja lehelt"));
    }
    if (findings.contains(NOT_PUBLISHED.name())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + " "
                  + date(confirmation.pricingDate())
                  + ": hinnapäeva NAV-i pole hinnaandmetes, hinda ei saanud kontrollida",
              received(confirmation),
              "Võrdle hinda fondivalitseja lehega ja teavita IT-d, et fondi NAV hinnaandmetesse ei"
                  + " jõua"));
    }
    if (findings.contains(LATE.name())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + ": hinnapäev "
                  + date(confirmation.pricingDate())
                  + " on "
                  + result.navDatesAfterReceipt()
                  + " NAV-i päeva pärast korralduse saamist (lubatud "
                  + properties.maxNavDatesAfterReceipt()
                  + ")",
              received(confirmation),
              "Küsi SEB-lt, miks korraldus hiljem täideti"));
    }
    return blocks;
  }

  private String block(MissingSebFundConfirmation missing) {
    return lines(
        missing.fund().name()
            + " "
            + missing.isin()
            + " "
            + side(missing.side())
            + " "
            + date(missing.tradeDate())
            + ": SEB tehingukinnitust pole "
            + properties.businessDaysToWait()
            + " tööpäeva järel",
        "Tehing "
            + missing.tradeReference()
            + " on SEB ootel tehingute raportis, kogus "
            + missing.quantity().toPlainString(),
        "Kontrolli funds@ postkasti ja küsi kinnitust SEB-lt");
  }

  private static String block(UnreadableSebFundConfirmation unreadable) {
    return lines(
        "SEB tehingukinnitust ei õnnestunud lugeda: " + unreadable.key(),
        "Põhjus: " + unreadable.reason(),
        "Kontrolli PDF-i käsitsi");
  }

  private static String block(SebFundConfirmationCheckFailure failure) {
    return lines(
        "SEB tehingukinnituste kontroll jäi pooleli: " + failure.step(),
        "Põhjus: " + failure.reason(),
        "Teavita IT-d ja kontrolli seda osa seni käsitsi");
  }

  private boolean send(String message) {
    try {
      notificationService.sendMessage(message, INVESTMENT);
      return true;
    } catch (RuntimeException e) {
      log.error(
          "Failed to send SEB fund confirmation digest to Slack: error={}", e.getMessage(), e);
      return false;
    }
  }

  private static String trade(SebFundConfirmation confirmation) {
    return confirmation.fund().name() + " " + confirmation.isin() + " " + side(confirmation.side());
  }

  private static String received(SebFundConfirmation confirmation) {
    return "Tehing "
        + confirmation.tradeReference()
        + ", SEB sai korralduse "
        + DATE_TIME.format(confirmation.orderReceivedAt().atZone(SEB_TIME_ZONE));
  }

  private static String side(TransactionType side) {
    return side == BUY ? "ost" : "müük";
  }

  private static String date(LocalDate date) {
    return DATE.format(date);
  }

  private static String plain(@Nullable BigDecimal value) {
    return Objects.requireNonNull(value).stripTrailingZeros().toPlainString();
  }

  private static String signedPercent(@Nullable BigDecimal percent) {
    return String.format(Locale.ROOT, "%+.2f%%", Objects.requireNonNull(percent));
  }

  private static String lines(String what, String cause, String action) {
    return what + "\n" + cause + "\n" + action;
  }

  private record NewFindings(SebFundConfirmationOutcome outcome, List<String> findings) {}
}
