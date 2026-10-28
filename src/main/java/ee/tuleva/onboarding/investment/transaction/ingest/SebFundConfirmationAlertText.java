package ee.tuleva.onboarding.investment.transaction.ingest;

import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmation.SEB_TIME_ZONE;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.FAR_FROM_NAV;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.NavStatus.NOT_PUBLISHED;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.ReportStatus.NOT_FOUND;
import static ee.tuleva.onboarding.investment.transaction.ingest.SebFundConfirmationResult.TimingStatus.LATE;

import ee.tuleva.onboarding.investment.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
class SebFundConfirmationAlertText {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final SebFundConfirmationProperties properties;

  List<String> blocks(SebFundConfirmationOutcome outcome, List<String> findings) {
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
    if (findings.contains(result.registryMismatchFinding())) {
      blocks.add(
          lines(
              trade(confirmation)
                  + " "
                  + date(confirmation.pricingDate())
                  + ": SEB tehingukinnitus ei klapi tehinguregistriga ("
                  + String.join("; ", result.registry().differences())
                  + ")",
              received(confirmation),
              "Kontrolli, kumb on õige, ja paranda täitmine tehinguregistris või küsi SEB-lt"
                  + " parandust"));
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

  String block(MissingSebFundConfirmation missing) {
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

  String block(UnreadableSebFundConfirmation unreadable) {
    return lines(
        "SEB tehingukinnitust ei õnnestunud lugeda: " + unreadable.key(),
        "Põhjus: " + unreadable.reason(),
        "Kontrolli PDF-i käsitsi");
  }

  String block(MisnamedSebFundConfirmation misnamed) {
    return lines(
        "SEB tehingukinnituse failinimi ja sisu ei klapi: " + misnamed.key(),
        "Failinimi viitab tehingule "
            + misnamed.nameReference()
            + ", PDF-is on tehing "
            + misnamed.contentReference(),
        "Kontrolli funds@ postkastist, kas tehingu "
            + misnamed.nameReference()
            + " kinnitus on olemas");
  }

  String block(SebFundConfirmationCheckFailure failure) {
    return lines(
        "SEB tehingukinnituste kontroll jäi pooleli: " + failure.step(),
        "Põhjus: " + failure.reason(),
        "Teavita IT-d ja kontrolli seda osa seni käsitsi");
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
}
