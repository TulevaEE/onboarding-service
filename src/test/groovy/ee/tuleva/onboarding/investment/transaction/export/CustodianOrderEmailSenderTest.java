package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustodianOrderEmailSenderTest {

  private static final Instant TIMESTAMP = Instant.parse("2026-01-15T10:00:00Z");

  @Mock private EmailService emailService;

  @Test
  void send_buildsMessageAndSendsWhenEnabledAndConfigured() {
    var properties =
        new CustodianOrderEmailProperties(
            true, List.of("trustee@seb.ee"), List.of("funds@tuleva.ee"));
    var factory = new CustodianOrderMessageFactory(properties);
    var sender = new CustodianOrderEmailSender(properties, factory, emailService);
    given(emailService.sendSystemEmail(any())).willReturn(true);

    var message = factory.create(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1, 2}));

    boolean sent = sender.send(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1, 2}));

    assertThat(sent).isTrue();
    verify(emailService).sendSystemEmail(messageMatching(message));
  }

  @Test
  void send_doesNothingWhenDisabled() {
    var properties =
        new CustodianOrderEmailProperties(
            false, List.of("trustee@seb.ee"), List.of("funds@tuleva.ee"));
    var sender =
        new CustodianOrderEmailSender(
            properties, new CustodianOrderMessageFactory(properties), emailService);

    boolean sent = sender.send(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1}));

    assertThat(sent).isFalse();
    verifyNoInteractions(emailService);
  }

  @Test
  void send_doesNothingWhenNoRecipientsConfigured() {
    var properties = new CustodianOrderEmailProperties(true, List.of(), List.of());
    var sender =
        new CustodianOrderEmailSender(
            properties, new CustodianOrderMessageFactory(properties), emailService);

    boolean sent = sender.send(TUV100, TIMESTAMP, Map.of("sebFundXlsx", new byte[] {1}));

    assertThat(sent).isFalse();
    verifyNoInteractions(emailService);
  }

  @Test
  void send_doesNothingWhenAllExportsEmpty() {
    var properties = new CustodianOrderEmailProperties(true, List.of("trustee@seb.ee"), List.of());
    var sender =
        new CustodianOrderEmailSender(
            properties, new CustodianOrderMessageFactory(properties), emailService);

    boolean sent = sender.send(TUV100, TIMESTAMP, Map.of());

    assertThat(sent).isFalse();
    verify(emailService, never()).sendSystemEmail(any());
  }

  private static MandrillMessage messageMatching(MandrillMessage expected) {
    return org.mockito.ArgumentMatchers.argThat(
        actual ->
            actual.getSubject().equals(expected.getSubject())
                && actual.getTo().size() == expected.getTo().size()
                && actual.getAttachments().size() == expected.getAttachments().size());
  }
}
