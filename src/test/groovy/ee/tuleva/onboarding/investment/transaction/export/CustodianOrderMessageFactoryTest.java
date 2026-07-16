package ee.tuleva.onboarding.investment.transaction.export;

import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.CC;
import static com.microtripit.mandrillapp.lutung.view.MandrillMessage.Recipient.Type.TO;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage.MessageContent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustodianOrderMessageFactoryTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");

  private final CustodianOrderEmailProperties properties =
      new CustodianOrderEmailProperties(
          true, List.of("trustee@seb.ee"), List.of("funds@tuleva.ee"));
  private final CustodianOrderMessageFactory factory = new CustodianOrderMessageFactory(properties);

  @Test
  void create_setsFromSubjectAndRecipients() {
    var message = factory.create(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1, 2}));

    assertThat(message.getFromEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getFromName()).isEqualTo("Tuleva");
    assertThat(message.getSubject()).contains("TUV100");
    assertThat(message.getTo()).hasSize(2);
    assertThat(message.getTo().get(0).getEmail()).isEqualTo("trustee@seb.ee");
    assertThat(message.getTo().get(0).getType()).isEqualTo(TO);
    assertThat(message.getTo().get(1).getEmail()).isEqualTo("funds@tuleva.ee");
    assertThat(message.getTo().get(1).getType()).isEqualTo(CC);
  }

  @Test
  void create_attachesAllNonEmptyExportWorkbooks() {
    var message =
        factory.create(
            TUV100,
            TIMESTAMP,
            Map.of(
                "sebFundXlsx", new byte[] {1, 2},
                "sebEtfXlsx", new byte[] {3, 4},
                "ftEtfXlsx", new byte[] {5, 6}));

    assertThat(message.getAttachments())
        .hasSize(3)
        .extracting(MessageContent::getName)
        .anySatisfy(
            name -> assertThat(name).contains("SEB").contains("indeksfondid").endsWith(".csv"))
        .anySatisfy(name -> assertThat(name).contains("SEB").contains("ETF").endsWith(".xlsx"))
        .anySatisfy(name -> assertThat(name).contains("FT").contains("TUV100").endsWith(".xlsx"));
  }

  @Test
  void create_sebFundAttachmentHasCsvMimeTypeAndFilename() {
    var message = factory.create(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1, 2}));

    var attachment = message.getAttachments().getFirst();
    assertThat(attachment.getName()).isEqualTo("SEB_TUV100_indeksfondid_2026-01-15T10_00_00.csv");
    assertThat(attachment.getType()).isEqualTo("text/csv");
  }

  @Test
  void create_sebEtfAndFtEtfAttachmentsKeepXlsxMimeType() {
    var message =
        factory.create(
            TUV100,
            TIMESTAMP,
            Map.of(
                "sebEtfXlsx", new byte[] {3, 4},
                "ftEtfXlsx", new byte[] {5, 6}));

    assertThat(message.getAttachments())
        .allSatisfy(
            attachment ->
                assertThat(attachment.getType())
                    .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  @Test
  void create_skipsEmptyExportBytes() {
    var message =
        factory.create(
            TUV100,
            TIMESTAMP,
            Map.of(
                "sebFundXlsx", new byte[] {1, 2},
                "sebEtfXlsx", new byte[0],
                "ftEtfXlsx", new byte[0]));

    assertThat(message.getAttachments()).hasSize(1);
  }
}
