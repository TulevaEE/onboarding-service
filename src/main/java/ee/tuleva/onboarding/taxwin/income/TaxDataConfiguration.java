package ee.tuleva.onboarding.taxwin.income;

import static java.time.Duration.ofSeconds;

import ee.tuleva.onboarding.taxwin.TaxHeadroomProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration
@EnableConfigurationProperties(TaxHeadroomProperties.class)
public class TaxDataConfiguration {

  static Jaxb2Marshaller taxDataMarshaller() {
    var marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath("ee.tuleva.onboarding.taxwin.income.generated");
    return marshaller;
  }

  @Bean
  public WebServiceTemplate taxDataWebServiceTemplate(TaxHeadroomProperties properties) {
    var template = new WebServiceTemplate(taxDataMarshaller());
    template.setDefaultUri(properties.xRoadUrl());
    var sender = new HttpUrlConnectionMessageSender();
    sender.setConnectionTimeout(ofSeconds(3));
    sender.setReadTimeout(ofSeconds(10));
    template.setMessageSender(sender);
    return template;
  }
}
