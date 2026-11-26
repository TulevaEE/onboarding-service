package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.FT;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.UUID;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Locale.ROOT;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

final class OrderEmailSlackText {

  private static final String SEND_THE_UUID_FILE_BY_HAND = "saada UUID-fail SEB-le käsitsi";
  static final String SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART =
      "Mida teha: " + SEND_THE_UUID_FILE_BY_HAND + ". Tehingukorraldused on eraldi kirjades.";
  private static final String NO_RESULT_RECORDED =
      "Saatmise tulemust ei salvestatud: teenus võis saatmise ajal seiskuda.";

  private OrderEmailSlackText() {}

  static String sent(OrderEmailSummary summary, MandrillMessage message) {
    return "✅ %skiri saadetud: %s\n„%s“, saatja %s. Mandrill võttis vastu."
        .formatted(
            prefix(summary.email()), numbers(summary), message.getSubject(), message.getFromName());
  }

  static String sentButNotToEveryCopy(OrderEmailSummary summary, String cause) {
    return ("%s %skiri läks välja, aga mitte kõigile koopiasaajatele: %s\n%s\n"
            + "Mida teha: kirja uuesti ei saada. Uuri, miks Mandrill koopia tagasi lükkas.")
        .formatted(icon(summary.email()), prefix(summary.email()), numbers(summary), cause);
  }

  static String notSent(OrderEmailSummary summary, String cause) {
    var email = summary.email();
    var action =
        email == UUID
            ? SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART
            : "Mida teha: paranda põhjus ja saada fail käsitsi.";
    return "%s %skiri jäi saatmata: %s\n%s\n%s"
        .formatted(icon(email), prefix(email), numbers(summary), cause, action);
  }

  static String notBuilt(OrderEmailSummary summary, String problem) {
    return notSent(summary, "Kirja ei saanud koostada: " + problem);
  }

  static String resultUnknown(OrderEmailSummary summary, String cause) {
    var email = summary.email();
    var byHand = email == UUID ? SEND_THE_UUID_FILE_BY_HAND : "saada fail käsitsi";
    return ("%s %skirja tulemus teadmata: %s\n%s\n"
            + "Mida teha: kiri võis välja minna. Kui Cc-koopia on olemas, ära saada uuesti."
            + " Kui pole, %s.")
        .formatted(icon(email), prefix(email), numbers(summary), cause, byHand);
  }

  static String noResultRecorded(OrderEmailSummary summary) {
    return resultUnknown(summary, NO_RESULT_RECORDED);
  }

  static Severity problemSeverity(OrderEmail email) {
    return email == UUID ? WARNING : ERROR;
  }

  private static String icon(OrderEmail email) {
    return icon(problemSeverity(email));
  }

  static String icon(Severity severity) {
    return severity == WARNING ? "🟠" : "🔴";
  }

  static String prefix(OrderEmail email) {
    return switch (email) {
      case FT -> "FT ";
      case SEB_ETF -> "SEB ETF-ide ";
      case SEB_FUND -> "SEB indeksfondide ";
      case UUID -> "SEB UUID-";
    };
  }

  private static String numbers(OrderEmailSummary summary) {
    if (summary.email() == UUID) {
      return count(summary.orderCount(), "viide", "viidet");
    }
    var withAmount = summary.orderCount() - summary.ordersWithoutAmount();
    var numbers = new StringBuilder(count(summary.orderCount(), "korraldus", "korraldust"));
    if (withAmount > 0) {
      numbers.append(", ").append(approximately(summary.email())).append(euros(summary.amount()));
    }
    if (summary.ordersWithoutAmount() > 0) {
      numbers
          .append(withAmount > 0 ? " + " + summary.ordersWithoutAmount() : "")
          .append(" kogusena");
    }
    return numbers.toString();
  }

  private static String count(int count, String one, String many) {
    return count + " " + (count == 1 ? one : many);
  }

  private static String approximately(OrderEmail email) {
    return email == FT || email == SEB_ETF ? "≈ " : "";
  }

  private static String euros(BigDecimal amount) {
    var symbols = new DecimalFormatSymbols(ROOT);
    symbols.setGroupingSeparator(' ');
    symbols.setDecimalSeparator(',');
    var format = new DecimalFormat("#,##0.00", symbols);
    format.setRoundingMode(HALF_UP);
    return format.format(amount) + " €";
  }
}
