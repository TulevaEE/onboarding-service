package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.FT;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.SEB_ETF;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.SEB_FUND;
import static ee.tuleva.onboarding.investment.transaction.export.OrderEmail.UUID;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderEmailSlackTextTest {

  private static final OrderEmailSummary SIX_FT_ORDERS =
      new OrderEmailSummary("FT:12,13", FT, 6, 0, new BigDecimal("2463732.23"));
  private static final OrderEmailSummary ONE_SEB_FUND_ORDER =
      new OrderEmailSummary("SEB_FUND:12,13", SEB_FUND, 1, 0, new BigDecimal("282868"));
  private static final OrderEmailSummary SIX_UUID_REFERENCES =
      new OrderEmailSummary("UUID:12,13", UUID, 6, 0, new BigDecimal("2463732.23"));

  @Test
  void anAcceptedEmailIsGreen_namingTheEmailItsOrdersTheSubjectAndTheSender() {
    var message = new MandrillMessage();
    message.setSubject("Tuleva ETF orders 26.11.2026 – MOC");
    message.setFromName("Fund Manager");

    assertThat(OrderEmailSlackText.sent(SIX_FT_ORDERS, message))
        .isEqualTo(
            """
            ✅ FT email sent: 6 orders, ≈ EUR 2,463,732.23
            "Tuleva ETF orders 26.11.2026 – MOC", from Fund Manager. Accepted by Mandrill.""");
  }

  @Test
  void anOrderEmailNotSent_isRed_andSaysToSendTheFileByHand() {
    assertThat(
            OrderEmailSlackText.notSent(
                ONE_SEB_FUND_ORDER, "Not accepted: desk@example.com rejected (hard-bounce)"))
        .isEqualTo(
            """
            🔴 SEB index fund email NOT sent: 1 order, EUR 282,868.00
            Not accepted: desk@example.com rejected (hard-bounce)
            What to do: fix the cause, then send the file by hand.""");
  }

  @Test
  void theUuidEmailNotSent_isAmber_sinceTheOrdersGoInEmailsOfTheirOwn() {
    assertThat(OrderEmailSlackText.notSent(SIX_UUID_REFERENCES, "ConnectException"))
        .isEqualTo(
            """
            🟠 SEB UUID email NOT sent: 6 references
            ConnectException
            What to do: send the UUID file to SEB by hand. The orders go in emails of their own.""");
    assertThat(OrderEmailSlackText.problemSeverity(UUID)).isEqualTo(WARNING);
    assertThat(OrderEmailSlackText.problemSeverity(FT)).isEqualTo(ERROR);
    assertThat(OrderEmailSlackText.problemSeverity(SEB_FUND)).isEqualTo(ERROR);
    assertThat(OrderEmailSlackText.problemSeverity(SEB_ETF)).isEqualTo(ERROR);
  }

  @Test
  void anEmailThatCouldNotBeBuilt_isNotSent_withTheProblemAsItsCause() {
    assertThat(
            OrderEmailSlackText.notBuilt(
                SIX_FT_ORDERS, "java.lang.IllegalStateException: no file name"))
        .isEqualTo(
            """
            🔴 FT email NOT sent: 6 orders, ≈ EUR 2,463,732.23
            The email could not be built: java.lang.IllegalStateException: no file name
            What to do: fix the cause, then send the file by hand.""");
  }

  @Test
  void anUnknownResult_saysToAskTheDeskBeforeSendingTheOrdersAgain() {
    assertThat(OrderEmailSlackText.resultUnknown(SIX_FT_ORDERS, "Read timed out"))
        .isEqualTo(
            """
            🔴 FT email result UNKNOWN: 6 orders, ≈ EUR 2,463,732.23
            Read timed out
            What to do: the email may have gone out. Before sending again, ask the desk \
            whether the orders arrived: a missing Cc copy doesn't show that.""");
    assertThat(OrderEmailSlackText.noResultRecorded(SIX_UUID_REFERENCES))
        .isEqualTo(
            """
            🟠 SEB UUID email result UNKNOWN: 6 references
            No send result was recorded: the service may have stopped while sending.
            What to do: the email may have gone out. If there is no Cc copy, \
            send the UUID file to SEB by hand.""");
  }

  @Test
  void anEmailThatMissedACopy_wentOut_soItSaysNotToSendItAgain() {
    assertThat(
            OrderEmailSlackText.sentButNotToEveryCopy(
                SIX_FT_ORDERS, "Not accepted: copy@example.com rejected (hard-bounce)"))
        .isEqualTo(
            """
            🔴 FT email went out, but not to every copy: 6 orders, ≈ EUR 2,463,732.23
            Not accepted: copy@example.com rejected (hard-bounce)
            What to do: don't send the email again. Find out why Mandrill rejected the copy.""");
  }

  @Test
  void ordersGivenAsAQuantity_areCountedApartFromTheAmount() {
    var someByQuantity =
        new OrderEmailSummary("SEB_ETF:12", SEB_ETF, 3, 1, new BigDecimal("1000.005"));
    var allByQuantity = new OrderEmailSummary("SEB_FUND:12", SEB_FUND, 2, 2, BigDecimal.ZERO);

    assertThat(OrderEmailSlackText.notSent(someByQuantity, "cause"))
        .startsWith("🔴 SEB ETF email NOT sent: 3 orders, ≈ EUR 1,000.01 + 1 by quantity\n");
    assertThat(OrderEmailSlackText.notSent(allByQuantity, "cause"))
        .startsWith("🔴 SEB index fund email NOT sent: 2 orders by quantity\n");
  }
}
