package ee.tuleva.onboarding.investment.transaction.export;

import static ee.tuleva.onboarding.notification.email.EmailSendResult.Outcome.FAILED;
import static org.assertj.core.api.Assertions.assertThat;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import org.junit.jupiter.api.Test;

class CustodianOrderEmailConfigurationTest {

  private static final String MANDRILL_API_URL = "https://mandrill.example.com/api/1.0";

  private final CustodianOrderEmailConfiguration configuration =
      new CustodianOrderEmailConfiguration();

  @Test
  void sendsNothingWhenNoOrderEmailKeyIsConfigured() {
    var transport = configuration.custodianOrderEmailTransport(null, MANDRILL_API_URL);

    assertThat(transport.send(new MandrillMessage()).outcome()).isEqualTo(FAILED);
  }

  @Test
  void sendsNothingWhenTheOrderEmailKeyIsBlank() {
    var transport = configuration.custodianOrderEmailTransport(" ", MANDRILL_API_URL);

    assertThat(transport.send(new MandrillMessage()).outcome()).isEqualTo(FAILED);
  }
}
