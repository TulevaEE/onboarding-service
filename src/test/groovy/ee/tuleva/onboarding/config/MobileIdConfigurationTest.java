package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;

import ee.sk.mid.MidAuthenticationResponseValidator;
import ee.sk.mid.MidClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MobileIdConfigurationTest {

  private final MobileIdConfiguration configuration = new MobileIdConfiguration();

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        configuration,
        "trustStorePath",
        TestTrustStores.writeTrustStoreContainingASelfSignedCertificate());
    ReflectionTestUtils.setField(
        configuration, "relyingPartyUUID", "00000000-0000-0000-0000-000000000000");
    ReflectionTestUtils.setField(configuration, "relyingPartyName", "Tuleva Test");
    ReflectionTestUtils.setField(configuration, "hostUrl", "https://mid.sk.ee/mid-api");
    ReflectionTestUtils.setField(configuration, "pollingSleepTimeoutSeconds", 1);
  }

  @Test
  void mobileIDClientLoadsTheConfiguredTrustStoreAndIsUsable() {
    MidClient client = configuration.mobileIDClient(TestTrustStores.RESOURCE_LOADER);

    assertThat(client).isNotNull();
    assertThat(client.getMobileIdConnector()).isNotNull();
  }

  @Test
  void mobileIDConnectorComesFromTheConfiguredClient() {
    MidClient client = configuration.mobileIDClient(TestTrustStores.RESOURCE_LOADER);

    var connector = configuration.mobileIDConnector(client);

    assertThat(connector).isSameAs(client.getMobileIdConnector());
  }

  @Test
  void mobileIDSessionStatusPollerComesFromTheConfiguredClient() {
    MidClient client = configuration.mobileIDClient(TestTrustStores.RESOURCE_LOADER);

    var poller = configuration.mobileIDSessionStatusPoller(client);

    assertThat(poller).isSameAs(client.getSessionStatusPoller());
  }

  @Test
  void mobileIDValidatorLoadsTheConfiguredTrustStore() {
    MidAuthenticationResponseValidator validator =
        configuration.mobileIDValidator(TestTrustStores.RESOURCE_LOADER);

    assertThat(validator).isNotNull();
  }
}
