package ee.tuleva.onboarding.investment.transaction.export;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient;
import ee.tuleva.onboarding.fund.TulevaFund;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class CustodianOrderMessageFactory {

  private static final String XLSX_MIME_TYPE =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private static final String CSV_MIME_TYPE = "text/csv";

  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH_mm_ss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter SUBJECT_DATE =
      DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC);

  private static final Map<String, String> FILE_NAME_PATTERNS =
      Map.of(
          "sebFundXlsx", "SEB_%s_indeksfondid_%s.csv",
          "sebEtfXlsx", "SEB_%s_ETF_tehingud_%s.xlsx",
          "ftEtfXlsx", "FT_%s_ETF_orders_%s.xlsx");

  private static final Map<String, String> MIME_TYPES =
      Map.of(
          "sebFundXlsx", CSV_MIME_TYPE,
          "sebEtfXlsx", XLSX_MIME_TYPE,
          "ftEtfXlsx", XLSX_MIME_TYPE);

  private final CustodianOrderEmailProperties properties;

  MandrillMessage create(TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    var message = new MandrillMessage();
    message.setFromEmail("funds@tuleva.ee");
    message.setFromName("Tuleva");
    message.setSubject(
        fund.getCode() + " ostu-/müügikorraldused " + SUBJECT_DATE.format(timestamp));
    message.setHtml(
        """
        <p>Tere,</p>
        <p>Manuses on %s fondi ostu-/müügikorralduste failid.</p>
        <p>See on automaatne kiri.</p>
        <p>Parimat,<br>Tuleva robot</p>
        """
            .formatted(fund.getCode()));
    message.setPreserveRecipients(true);
    message.setTo(buildRecipients());
    message.setAttachments(buildAttachments(fund, timestamp, exports));
    return message;
  }

  private List<Recipient> buildRecipients() {
    List<Recipient> recipients = new ArrayList<>();
    for (String to : properties.to()) {
      var recipient = new Recipient();
      recipient.setEmail(to);
      recipient.setType(TO);
      recipients.add(recipient);
    }
    for (String cc : properties.cc()) {
      var recipient = new Recipient();
      recipient.setEmail(cc);
      recipient.setType(CC);
      recipients.add(recipient);
    }
    return recipients;
  }

  private List<MessageContent> buildAttachments(
      TulevaFund fund, Instant timestamp, Map<String, byte[]> exports) {
    var fileTimestamp = TIMESTAMP.format(timestamp);
    List<MessageContent> attachments = new ArrayList<>();
    FILE_NAME_PATTERNS.forEach(
        (exportKey, namePattern) -> {
          var content = exports.get(exportKey);
          if (content != null && content.length > 0) {
            var attachment = new MessageContent();
            attachment.setName(namePattern.formatted(fund.getCode(), fileTimestamp));
            attachment.setType(MIME_TYPES.get(exportKey));
            attachment.setContent(Base64.getEncoder().encodeToString(content));
            attachments.add(attachment);
          }
        });
    return attachments;
  }
}
