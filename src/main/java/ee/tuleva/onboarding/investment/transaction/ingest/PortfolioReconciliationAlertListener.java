package ee.tuleva.onboarding.investment.transaction.ingest;

import ee.tuleva.onboarding.investment.transaction.ingest.PortfolioReconciliationMismatchEvent.MismatchEntry;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class PortfolioReconciliationAlertListener {

  private final AlertMandrillMessageFactory messageFactory;
  private final EmailService emailService;

  @EventListener
  public void onMismatch(PortfolioReconciliationMismatchEvent event) {
    var subject =
        "[HOIATUS] Portfellipositsioonide lahknevus – "
            + event.fund().getCode()
            + " – "
            + event.asOfDate();
    var body = buildBody(event);

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, body));
    if (sent) {
      log.info(
          "Sent portfolio reconciliation alert: fundCode={}, asOfDate={}, mismatchCount={}",
          event.fund().getCode(),
          event.asOfDate(),
          event.mismatches().size());
    } else {
      log.error(
          "Failed to send portfolio reconciliation alert: fundCode={}, asOfDate={}",
          event.fund().getCode(),
          event.asOfDate());
    }
  }

  @EventListener
  public void onSkipped(PortfolioReconciliationSkippedEvent event) {
    var subject =
        "[HOIATUS] Portfelli võrdlus jäi tegemata – "
            + event.fund().getCode()
            + " – "
            + event.asOfDate();

    boolean sent = emailService.sendSystemEmail(messageFactory.create(subject, buildBody(event)));
    if (sent) {
      log.info(
          "Sent portfolio reconciliation skipped alert: fundCode={}, asOfDate={}",
          event.fund().getCode(),
          event.asOfDate());
    } else {
      log.error(
          "Failed to send portfolio reconciliation skipped alert: fundCode={}, asOfDate={}",
          event.fund().getCode(),
          event.asOfDate());
    }
  }

  private static String buildBody(PortfolioReconciliationSkippedEvent event) {
    boolean ledgerMissing = event.isLedgerMissing();
    String missingSource =
        ledgerMissing ? "Tuleva cost-basis pearaamat" : "SEB POSITIONS-põhine nav_report";
    String availableSource =
        ledgerMissing ? "SEB POSITIONS-põhine nav_report" : "Tuleva cost-basis pearaamat";
    String whatToCheck =
        ledgerMissing
            ? "Kontrolli, kas fondil on investment_portfolio_baseline kirje ja kas cost-basis töö on selle kuupäeva kohta jooksnud."
            : "Kontrolli, kas selle kuupäeva NAV arvutus jooksis ja kas nav_report sai avaldatud.";

    return """
        %s ei sisalda selle fondi ja kuupäeva kohta ühtegi väärtpaberipositsiooni, seega \
        positsioone ei saanud võrrelda. Kas lahknevus on või ei ole, jäi välja selgitamata.

        Kuupäev: %s

        Fond: %s (%s)

        %s näitab neid koguseid:

        %s
        %s Portfelli võrdlus jookseb ainult eelmise tööpäeva kohta, seega tuleb see kuupäev \
        pärast paranduse tegemist käsitsi uuesti võrrelda.
        """
        .formatted(
            missingSource,
            event.asOfDate(),
            event.fund().getCode(),
            event.fund().getDisplayName(),
            availableSource,
            formatTable(event.availableQuantities()),
            whatToCheck);
  }

  private static String formatTable(Map<String, BigDecimal> quantities) {
    StringBuilder table = new StringBuilder();
    table.append("ISIN          | Kogus\n");
    table.append("--------------+----------------\n");
    new TreeMap<>(quantities)
        .forEach(
            (isin, quantity) ->
                table
                    .append(pad(isin, 13))
                    .append(" | ")
                    .append(formatQuantity(quantity))
                    .append('\n'));
    return table.toString();
  }

  private static String buildBody(PortfolioReconciliationMismatchEvent event) {
    StringBuilder body = new StringBuilder();
    body.append(
            "Tuleva cost-basis pipeline'i ja SEB POSITIONS-põhise nav_report'i SECURITY kogused")
        .append(" ei klapi.\n\n")
        .append("Kuupäev: ")
        .append(event.asOfDate())
        .append('\n')
        .append('\n')
        .append("Fond: ")
        .append(event.fund().getCode())
        .append(" (")
        .append(event.fund().getDisplayName())
        .append(")\n")
        .append('\n')
        .append(formatTable(event));
    body.append('\n').append("Vajab käsitsi uurimist.\n");
    return body.toString();
  }

  private static String formatTable(PortfolioReconciliationMismatchEvent event) {
    StringBuilder table = new StringBuilder();
    table.append("ISIN          | Meie kogus     | Nende kogus    | Vahe\n");
    table.append("--------------+----------------+----------------+----------------\n");
    for (MismatchEntry entry : event.mismatches()) {
      table
          .append(pad(entry.isin(), 13))
          .append(" | ")
          .append(pad(formatQuantity(entry.ourQuantity()), 14))
          .append(" | ")
          .append(pad(formatQuantity(entry.theirQuantity()), 14))
          .append(" | ")
          .append(formatQuantity(entry.delta()))
          .append('\n');
    }
    return table.toString();
  }

  private static String formatQuantity(@Nullable BigDecimal value) {
    return value == null ? "(puudub)" : value.toPlainString();
  }

  private static String pad(String value, int width) {
    if (value.length() >= width) {
      return value;
    }
    StringBuilder sb = new StringBuilder(value);
    while (sb.length() < width) {
      sb.append(' ');
    }
    return sb.toString();
  }
}
