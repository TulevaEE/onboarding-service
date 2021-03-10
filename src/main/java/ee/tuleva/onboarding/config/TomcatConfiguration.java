package ee.tuleva.onboarding.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfiguration {

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerCustomizer() {
    return new EmbeddedTomcatCustomizer();
  }

  private static class EmbeddedTomcatCustomizer
      implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
      factory.addConnectorCustomizers(
          connector -> connector.setAttribute("relaxedQueryChars", "[]"));
    }
  }
}
