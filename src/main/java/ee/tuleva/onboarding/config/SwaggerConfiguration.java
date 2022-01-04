package ee.tuleva.onboarding.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfiguration {

  @Bean
  public OpenAPI accountIdentityApi() {
    return new OpenAPI().info(apiInfo());
  }

  private Info apiInfo() {
    Contact contact =
        new Contact()
            .name("Tuleva")
            .url("https://github.com/TulevaEE")
            .email("tonu.pekk@tuleva.ee");
    return new Info().title("Tuleva onboarding service").contact(contact).version("1.0");
  }
}
