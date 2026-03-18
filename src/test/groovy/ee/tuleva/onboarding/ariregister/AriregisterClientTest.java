package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.ResourceSource;

@SpringJUnitConfig(AriregisterClientTest.TestConfig.class)
class AriregisterClientTest {

  private static final Map<String, String> NS = Map.of("ar", "http://arireg.x-road.eu/producer/");

  @Autowired private WebServiceTemplate ariregisterWebServiceTemplate;
  @Autowired private AriregisterClient client;

  private MockWebServiceServer mockServer;

  @BeforeEach
  void setUp() {
    mockServer = MockWebServiceServer.createServer(ariregisterWebServiceTemplate);
  }

  @Test
  void getCompanyRelationships() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andExpect(xpath("//ar:ariregister_kasutajanimi", NS).evaluatesTo("testuser"))
        .andExpect(xpath("//ar:ariregister_parool", NS).evaluatesTo("testpass"))
        .andExpect(xpath("//ar:keel", NS).evaluatesTo("est"))
        .andRespond(withPayload(responsePayload()));

    var result = client.getCompanyRelationships("99000001");

    assertThat(result)
        .containsExactly(
            new CompanyRelationship(
                "F",
                "JUHL",
                "Juhatuse liige",
                "Jaan",
                "Tamm",
                "39901010000",
                LocalDate.of(1999, 1, 1),
                LocalDate.of(2017, 4, 3),
                null,
                new BigDecimal("50.00"),
                "Osaluse kaudu",
                "EST"),
            new CompanyRelationship(
                "F",
                "N",
                "Nõukogu liige",
                "Kati",
                "Kask",
                "48501010000",
                LocalDate.of(1985, 1, 1),
                LocalDate.of(2020, 1, 15),
                LocalDate.of(2025, 12, 31),
                null,
                null,
                "EST"),
            new CompanyRelationship(
                "J",
                "S",
                "Osanik",
                null,
                "Test Firma OÜ",
                "99000002",
                null,
                LocalDate.of(2018, 6, 1),
                null,
                new BigDecimal("25.50"),
                null,
                "EST"));

    mockServer.verify();
  }

  @Test
  void filtersOutExpiredRelationships() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andRespond(withPayload(responsePayload()));

    var asOf = LocalDate.of(2026, 3, 1);
    var result = client.getActiveCompanyRelationships("99000001", asOf);

    assertThat(result)
        .extracting(CompanyRelationship::lastName)
        .containsExactly("Tamm", "Test Firma OÜ");

    mockServer.verify();
  }

  @Test
  void filtersOutFutureDatedRelationships() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andRespond(withPayload(responsePayload()));

    var asOf = LocalDate.of(2017, 6, 1);
    var result = client.getActiveCompanyRelationships("99000001", asOf);

    assertThat(result).extracting(CompanyRelationship::lastName).containsExactly("Tamm");

    mockServer.verify();
  }

  private static ResourceSource responsePayload() throws Exception {
    return new ResourceSource(
        new ClassPathResource("ariregister/ettevottegaSeotudIsikud_v1_response.xml"));
  }

  @org.springframework.context.annotation.Configuration
  static class TestConfig {

    @org.springframework.context.annotation.Bean
    Jaxb2Marshaller ariregisterMarshaller() {
      var marshaller = new Jaxb2Marshaller();
      marshaller.setContextPath("ee.tuleva.onboarding.ariregister.generated");
      return marshaller;
    }

    @org.springframework.context.annotation.Bean
    WebServiceTemplate ariregisterWebServiceTemplate(Jaxb2Marshaller ariregisterMarshaller) {
      var template = new WebServiceTemplate(ariregisterMarshaller);
      template.setDefaultUri("http://localhost");
      return template;
    }

    @org.springframework.context.annotation.Bean
    AriregisterProperties ariregisterProperties() {
      return new AriregisterProperties("http://localhost", "testuser", "testpass");
    }

    @org.springframework.context.annotation.Bean
    AriregisterClient ariregisterClient(
        WebServiceTemplate ariregisterWebServiceTemplate,
        AriregisterProperties ariregisterProperties) {
      return new AriregisterClient(ariregisterWebServiceTemplate, ariregisterProperties);
    }
  }
}
