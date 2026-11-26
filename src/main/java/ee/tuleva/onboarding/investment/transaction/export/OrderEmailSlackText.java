package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.FT;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.UUID;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static java.math.RoundingMode.HALF_UP;
import static java.util.Locale.ENGLISH;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.OperationsNotificationService.Severity;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

final class OrderEmailSlackText {

  private static final String SEND_THE_UUID_FILE_BY_HAND = "send the UUID file to SEB by hand";
  static final String SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART =
      "What to do: " + SEND_THE_UUID_FILE_BY_HAND + ". The orders go in emails of their own.";
  private static final String NO_RESULT_RECORDED =
      "No send result was recorded: the service may have stopped while sending.";

  private OrderEmailSlackText() {}

  static String sent(OrderEmailSummary summary, MandrillMessage message) {
    return "✅ %semail sent: %s\n\"%s\", from %s. Accepted by Mandrill."
        .formatted(
            prefix(summary.email()), numbers(summary), message.getSubject(), message.getFromName());
  }

  static String sentButNotToEveryCopy(OrderEmailSummary summary, String cause) {
    return ("%s %semail went out, but not to every copy: %s\n%s\n"
            + "What to do: don't send the email again. Find out why Mandrill rejected the copy.")
        .formatted(icon(summary.email()), prefix(summary.email()), numbers(summary), cause);
  }

  static String notSent(OrderEmailSummary summary, String cause) {
    var email = summary.email();
    var action =
        email == UUID
            ? SEND_THE_UUID_FILE_BY_HAND_THE_ORDERS_WENT_APART
            : "What to do: fix the cause, then send the file by hand.";
    return "%s %semail NOT sent: %s\n%s\n%s"
        .formatted(icon(email), prefix(email), numbers(summary), cause, action);
  }

  static String notBuilt(OrderEmailSummary summary, String problem) {
    return notSent(summary, "The email could not be built: " + problem);
  }

  static String resultUnknown(OrderEmailSummary summary, String cause) {
    var email = summary.email();
    var action =
        email == UUID
            ? "If there is no Cc copy, " + SEND_THE_UUID_FILE_BY_HAND + "."
            : "Before sending again, ask the desk whether the orders arrived:"
                + " a missing Cc copy doesn't show that.";
    return "%s %semail result UNKNOWN: %s\n%s\nWhat to do: the email may have gone out. %s"
        .formatted(icon(email), prefix(email), numbers(summary), cause, action);
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
      case SEB_ETF -> "SEB ETF ";
      case SEB_FUND -> "SEB index fund ";
      case UUID -> "SEB UUID ";
    };
  }

  private static String numbers(OrderEmailSummary summary) {
    if (summary.email() == UUID) {
      return count(summary.orderCount(), "reference", "references");
    }
    var withAmount = summary.orderCount() - summary.ordersWithoutAmount();
    var numbers = new StringBuilder(count(summary.orderCount(), "order", "orders"));
    if (withAmount > 0) {
      numbers.append(", ").append(approximately(summary.email())).append(euros(summary.amount()));
    }
    if (summary.ordersWithoutAmount() > 0) {
      numbers
          .append(withAmount > 0 ? " + " + summary.ordersWithoutAmount() : "")
          .append(" by quantity");
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
    var format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(ENGLISH));
    format.setRoundingMode(HALF_UP);
    return "EUR " + format.format(amount);
  }
}
