package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@EnabledIfEnvironmentVariable(named = "ARIREGISTER_SMOKE_TESTS", matches = "true")
class AriregisterClientSmokeTest {

  @Test
  void fetchTulevaFondidRelationships() {
    var marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath(
        "ee.tuleva.onboarding.ariregister.generated"
            + ":ee.tuleva.onboarding.ariregister.generated.detailandmed");

    var template = new WebServiceTemplate(marshaller);
    template.setDefaultUri("https://ariregxmlv6.rik.ee/ariregxml");

    var properties =
        new AriregisterProperties(
            "https://ariregxmlv6.rik.ee/ariregxml",
            System.getenv("ARIREGISTER_USERNAME"),
            System.getenv("ARIREGISTER_PASSWORD"));

    var client = new AriregisterClient(template, properties);
    var relationships = client.getActiveCompanyRelationships("14118923", LocalDate.now());

    assertThat(relationships).isNotEmpty();
    relationships.forEach(
        r ->
            System.out.printf(
                "[%s] %s %s (%s) — %s (%s), from %s, ownership=%s, country=%s%n",
                r.personType(),
                r.firstName(),
                r.lastName(),
                r.personalCode(),
                r.role(),
                r.roleCode(),
                r.startDate(),
                r.ownershipPercent(),
                r.countryCode()));
  }

  @Test
  void fetchTulevaFondidDetails() {
    var marshaller = new Jaxb2Marshaller();
    marshaller.setContextPath(
        "ee.tuleva.onboarding.ariregister.generated"
            + ":ee.tuleva.onboarding.ariregister.generated.detailandmed");

    var template = new WebServiceTemplate(marshaller);
    template.setDefaultUri("https://ariregxmlv6.rik.ee/ariregxml");

    var properties =
        new AriregisterProperties(
            "https://ariregxmlv6.rik.ee/ariregxml",
            System.getenv("ARIREGISTER_USERNAME"),
            System.getenv("ARIREGISTER_PASSWORD"));

    var client = new AriregisterClient(template, properties);
    var details = client.getCompanyDetails("14118923");

    assertThat(details).isPresent();
    var d = details.get();
    System.out.printf(
        "Name=%s, Code=%s, Status=%s, Founded=%s, Address=%s, Activity=%s%n",
        d.getName(),
        d.getRegistryCode(),
        d.getStatus(),
        d.getFoundingDate(),
        d.getAddress(),
        d.getMainActivity());
  }
}
