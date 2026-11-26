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
            ✅ FT kiri saadetud: 6 korraldust, ≈ 2 463 732,23 €
            „Tuleva ETF orders 26.11.2026 – MOC“, saatja Fund Manager. Mandrill võttis vastu.""");
  }

  @Test
  void anOrderEmailNotSent_isRed_andSaysToSendTheFileByHand() {
    assertThat(
            OrderEmailSlackText.notSent(
                ONE_SEB_FUND_ORDER, "Vastu ei võetud: desk@example.com rejected (hard-bounce)"))
        .isEqualTo(
            """
            🔴 SEB indeksfondide kiri jäi saatmata: 1 korraldus, 282 868,00 €
            Vastu ei võetud: desk@example.com rejected (hard-bounce)
            Mida teha: paranda põhjus ja saada fail käsitsi.""");
  }

  @Test
  void theUuidEmailNotSent_isAmber_sinceTheOrdersGoInEmailsOfTheirOwn() {
    assertThat(OrderEmailSlackText.notSent(SIX_UUID_REFERENCES, "ConnectException"))
        .isEqualTo(
            """
            🟠 SEB UUID-kiri jäi saatmata: 6 viidet
            ConnectException
            Mida teha: saada UUID-fail SEB-le käsitsi. Tehingukorraldused on eraldi kirjades.""");
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
            🔴 FT kiri jäi saatmata: 6 korraldust, ≈ 2 463 732,23 €
            Kirja ei saanud koostada: java.lang.IllegalStateException: no file name
            Mida teha: paranda põhjus ja saada fail käsitsi.""");
  }

  @Test
  void anUnknownResult_saysToAskTheRecipientBeforeSendingTheOrdersAgain() {
    assertThat(OrderEmailSlackText.resultUnknown(SIX_FT_ORDERS, "Read timed out"))
        .isEqualTo(
            """
            🔴 FT kirja tulemus teadmata: 6 korraldust, ≈ 2 463 732,23 €
            Read timed out
            Mida teha: kiri võis välja minna. Enne uuesti saatmist küsi saajalt, \
            kas korraldused jõudsid: Cc-koopia puudumine seda ei näita.""");
    assertThat(OrderEmailSlackText.noResultRecorded(SIX_UUID_REFERENCES))
        .isEqualTo(
            """
            🟠 SEB UUID-kirja tulemus teadmata: 6 viidet
            Saatmise tulemust ei salvestatud: teenus võis saatmise ajal seiskuda.
            Mida teha: kiri võis välja minna. Kui Cc-koopiat pole, saada UUID-fail SEB-le käsitsi.""");
  }

  @Test
  void anEmailThatMissedACopy_wentOut_soItSaysNotToSendItAgain() {
    assertThat(
            OrderEmailSlackText.sentButNotToEveryCopy(
                SIX_FT_ORDERS, "Vastu ei võetud: copy@example.com rejected (hard-bounce)"))
        .isEqualTo(
            """
            🔴 FT kiri läks välja, aga mitte kõigile koopiasaajatele: 6 korraldust, ≈ 2 463 732,23 €
            Vastu ei võetud: copy@example.com rejected (hard-bounce)
            Mida teha: kirja uuesti ei saada. Uuri, miks Mandrill koopia tagasi lükkas.""");
  }

  @Test
  void ordersGivenAsAQuantity_areCountedApartFromTheAmount() {
    var someByQuantity =
        new OrderEmailSummary("SEB_ETF:12", SEB_ETF, 3, 1, new BigDecimal("1000.005"));
    var allByQuantity = new OrderEmailSummary("SEB_FUND:12", SEB_FUND, 2, 2, BigDecimal.ZERO);

    assertThat(OrderEmailSlackText.notSent(someByQuantity, "cause"))
        .startsWith("🔴 SEB ETF-ide kiri jäi saatmata: 3 korraldust, ≈ 1 000,01 € + 1 kogusena\n");
    assertThat(OrderEmailSlackText.notSent(allByQuantity, "cause"))
        .startsWith("🔴 SEB indeksfondide kiri jäi saatmata: 2 korraldust kogusena\n");
  }
}
