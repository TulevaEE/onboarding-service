package ee.tuleva.onboarding.ariregister;

import org.springframework.ws.client.core.WebServiceTemplate;

public final class AriregisterTestClients {

  private AriregisterTestClients() {}

  public static WebServiceTemplate webServiceTemplate() throws Exception {
    var marshaller = new AriregisterConfiguration().ariregisterMarshaller();
    marshaller.afterPropertiesSet();
    var template = new WebServiceTemplate(marshaller);
    template.setDefaultUri("http://localhost");
    return template;
  }

  public static AriregisterClient client(WebServiceTemplate webServiceTemplate) {
    return new AriregisterClient(
        webServiceTemplate, new AriregisterProperties("http://localhost", "testuser", "testpass"));
  }
}
