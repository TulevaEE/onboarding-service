package ee.tuleva.onboarding.investment.transaction.ingest;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioReconciliationAlertListenerTest {

  @Mock private EmailService emailService;

  private final AlertProperties alertProperties =
      new AlertProperties(List.of("funds@tuleva.ee"), List.of("taavi.pertman@tuleva.ee"));

  private PortfolioReconciliationAlertListener listener() {
    return new PortfolioReconciliationAlertListener(
        new AlertMandrillMessageFactory(alertProperties), emailService);
  }

  @Test
  void onMismatch_sendsAlertWithFormattedBody() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    PortfolioReconciliationMismatchEvent event =
        new PortfolioReconciliationMismatchEvent(
            TUK75,
            LocalDate.of(2026, 5, 18),
            List.of(
                new MismatchEntry(
                    "IE00BFG1TM61",
                    new BigDecimal("10005.0000"),
                    new BigDecimal("10000.0000"),
                    new BigDecimal("5.0000")),
                new MismatchEntry(
                    "IE0009FT4LX4", new BigDecimal("250.0000"), null, new BigDecimal("250.0000"))));

    listener().onMismatch(event);

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] Portfellipositsioonide lahknevus – TUK75 – 2026-05-18");

    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("taavi.pertman@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);

    assertThat(message.getText())
        .contains("Kuupäev: 2026-05-18")
        .contains("Fond: TUK75")
        .contains("Tuleva Maailma Aktsiate Pensionifond")
        .contains("IE00BFG1TM61")
        .contains("10005.0000")
        .contains("10000.0000")
        .contains("IE0009FT4LX4")
        .contains("(puudub)")
        // "IE0009FT4LX4" is 12 chars, padded to the 13-char ISIN column width (1 space),
        // followed by the literal " | " column separator: two spaces before the pipe in total.
        .contains("IE0009FT4LX4  | ");
  }

  @Test
  void onSkipped_isinAtLeastAsWideAsItsColumnIsPassedThroughUnchanged() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);
    String isinLongerThanColumnWidth = "IE00BFG1TM611234";

    listener()
        .onSkipped(
            new PortfolioReconciliationSkippedEvent(
                TUK75,
                LocalDate.of(2026, 5, 18),
                Map.of(),
                Map.of(isinLongerThanColumnWidth, new BigDecimal("12.0000"))));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    assertThat(captor.getValue().getText()).contains(isinLongerThanColumnWidth + " | 12.0000");
  }

  // An empty side means the comparison did not happen. Reporting it as a mismatch is what made
  // the missing baseline row look like a position divergence.
  @Test
  void onSkipped_listsTheReportedPositionsAndAsksForAManualRecheck() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    listener()
        .onSkipped(
            new PortfolioReconciliationSkippedEvent(
                TUK75,
                LocalDate.of(2026, 5, 18),
                Map.of(),
                Map.of(
                    "IE00BFG1TM61",
                    new BigDecimal("10005.0000"),
                    "IE0009FT4LX4",
                    new BigDecimal("250.0000"))));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());
    MandrillMessage message = captor.getValue();

    assertThat(message.getSubject())
        .isEqualTo("[HOIATUS] Portfelli võrdlus jäi tegemata – TUK75 – 2026-05-18");
    assertThat(message.getText())
        .doesNotContain("See EI ole positsioonide lahknevus")
        .contains("Kuupäev: 2026-05-18")
        .contains("Fond: TUK75 (Tuleva Maailma Aktsiate Pensionifond)")
        .contains("IE0009FT4LX4")
        .contains("250.0000")
        .contains("IE00BFG1TM61")
        .contains("10005.0000")
        .contains("investment_portfolio_baseline")
        .contains("käsitsi uuesti");
  }

  @Test
  void onSkipped_listsOurOwnLedgerWhenTheNavReportIsTheMissingSide() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(true);

    listener()
        .onSkipped(
            new PortfolioReconciliationSkippedEvent(
                TUK75,
                LocalDate.of(2026, 5, 18),
                Map.of("IE00BFG1TM61", new BigDecimal("10005.0000")),
                Map.of()));

    ArgumentCaptor<MandrillMessage> captor = ArgumentCaptor.forClass(MandrillMessage.class);
    verify(emailService).sendSystemEmail(captor.capture());

    assertThat(captor.getValue().getText())
        .doesNotContain("See EI ole positsioonide lahknevus")
        .contains("SEB POSITIONS-põhine nav_report ei sisalda")
        .contains("Tuleva cost-basis pearaamat näitab neid koguseid")
        .contains("IE00BFG1TM61")
        .contains("10005.0000")
        .contains("NAV arvutus")
        .contains("käsitsi uuesti");
  }

  @Test
  void onSkipped_survivesAnEmailThatWasNotAccepted() {
    given(emailService.sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class)))
        .willReturn(false);

    listener()
        .onSkipped(
            new PortfolioReconciliationSkippedEvent(
                TUK75,
                LocalDate.of(2026, 5, 18),
                Map.of(),
                Map.of("IE00BFG1TM61", new BigDecimal("12"))));

    verify(emailService).sendSystemEmail(org.mockito.ArgumentMatchers.any(MandrillMessage.class));
  }
}
