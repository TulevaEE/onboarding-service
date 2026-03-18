package ee.tuleva.onboarding.ariregister;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
@EnableConfigurationProperties(AriregisterProperties.class)
class AriregisterConfiguration {

  @Bean
  Jaxb2Marshaller ariregisterMarshaller() {
    var marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath("ee.tuleva.onboarding.ariregister.generated");
    return marshaller;
  }

  @Bean
  WebServiceTemplate ariregisterWebServiceTemplate(
      Jaxb2Marshaller ariregisterMarshaller, AriregisterProperties properties) {
    var template = new WebServiceTemplate(ariregisterMarshaller);
    template.setDefaultUri(properties.url());
    return template;
  }
}
