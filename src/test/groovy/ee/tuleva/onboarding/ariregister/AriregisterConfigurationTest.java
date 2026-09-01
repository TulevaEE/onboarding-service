package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

class AriregisterConfigurationTest {

  private final AriregisterConfiguration configuration = new AriregisterConfiguration();

  @Test
  void marshallerScansTheGeneratedContextPath() {
    var marshaller = configuration.ariregisterMarshaller();

    assertThat(marshaller.getContextPath())
        .isEqualTo(
            "ee.tuleva.onboarding.ariregister.generated"
                + ":ee.tuleva.onboarding.ariregister.generated.detailandmed"
                + ":ee.tuleva.onboarding.ariregister.generated.kasusaajad");
  }

  @Test
  void webServiceTemplateIsConfiguredWithUriMarshallerAndTimeouts() {
    var marshaller = configuration.ariregisterMarshaller();
    var properties = new AriregisterProperties("https://ariregister.test/xtee", "user", "pass");

    var template = configuration.ariregisterWebServiceTemplate(marshaller, properties);

    assertThat(template.getDefaultUri()).isEqualTo("https://ariregister.test/xtee");
    assertThat(template.getMarshaller()).isSameAs(marshaller);
    assertThat(template.getUnmarshaller()).isSameAs(marshaller);
    assertThat(template.getMessageSenders()).hasSize(1);
    var sender = (HttpUrlConnectionMessageSender) template.getMessageSenders()[0];
    assertThat(ReflectionTestUtils.getField(sender, "connectionTimeout"))
        .isEqualTo(Duration.ofSeconds(30));
    assertThat(ReflectionTestUtils.getField(sender, "readTimeout"))
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void noBeneficialOwnersMeansAnEmptyListAndNoHiddenOwners() {
    assertThat(BeneficialOwners.none()).isEqualTo(new BeneficialOwners(java.util.List.of(), 0));
  }
}
