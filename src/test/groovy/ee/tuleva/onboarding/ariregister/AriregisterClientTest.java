package ee.tuleva.onboarding.ariregister;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.client.RequestMatchers.xpath;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
                "OSAN",
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

  @Test
  void getCompanyDetails() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andExpect(xpath("//ar:ariregister_kasutajanimi", NS).evaluatesTo("testuser"))
        .andExpect(xpath("//ar:ariregister_parool", NS).evaluatesTo("testpass"))
        .andExpect(xpath("//ar:yandmed", NS).evaluatesTo("true"))
        .andExpect(xpath("//ar:iandmed", NS).evaluatesTo("true"))
        .andRespond(withPayload(detailandmedResponsePayload()));

    var result = client.getCompanyDetails("99000001");

    assertThat(result).isPresent();
    var detail = result.get();
    assertThat(detail.getName()).isEqualTo("Test Firma OÜ");
    assertThat(detail.getRegistryCode()).isEqualTo("99000001");
    assertThat(detail.getStatus()).contains("R");
    assertThat(detail.getFoundingDate()).contains(LocalDate.of(2024, 9, 1));
    assertThat(detail.getAddress())
        .contains(
            new CompanyAddress(
                "Pärnu mnt 123, 11313 Tallinn",
                new AddressDetails("Pärnu mnt 123", "Tallinn", "11313", "EE")));
    assertThat(detail.getMainActivity()).contains("Fondide valitsemine");
    assertThat(detail.getRepresentationRights())
        .containsExactly(
            new RepresentationRight(
                "AINUESINDUS",
                "Juhatuse liige esindab äriühingut ainuisikuliselt",
                "Tehingute tegemiseks on nõutav nõukogu nõusolek",
                LocalDate.of(2024, 9, 1),
                LocalDate.of(2030, 12, 31),
                12345L));

    mockServer.verify();
  }

  @Test
  void getBeneficialOwners() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andExpect(xpath("//ar:ariregister_kasutajanimi", NS).evaluatesTo("testuser"))
        .andExpect(xpath("//ar:ariregister_parool", NS).evaluatesTo("testpass"))
        .andExpect(xpath("//ar:ainult_kehtivad", NS).evaluatesTo("true"))
        .andRespond(withPayload(beneficialOwnersResponsePayload()));

    var result = client.getBeneficialOwners("99000001");

    assertThat(result)
        .isEqualTo(
            new BeneficialOwners(
                List.of(
                    new BeneficialOwner("Jaan", "Tamm", "39901010000", "O"),
                    new BeneficialOwner("Mari", "Maasikas", null, "J")),
                0));
  }

  @Test
  void getBeneficialOwnersReportsHiddenOwnerCount() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andRespond(
            withPayload(
                new ResourceSource(
                    new ClassPathResource(
                        "ariregister/tegelikudKasusaajad_v2_hidden_response.xml"))));

    var result = client.getBeneficialOwners("99000001");

    assertThat(result)
        .isEqualTo(
            new BeneficialOwners(
                List.of(new BeneficialOwner("Jaan", "Tamm", "39901010000", "O")), 1));
  }

  @Test
  void getBeneficialOwnersReturnsEmptyWhenNoneRegistered() throws Exception {
    mockServer
        .expect(xpath("//ar:ariregistri_kood", NS).evaluatesTo("99000001"))
        .andRespond(
            withPayload(
                new ResourceSource(
                    new ClassPathResource(
                        "ariregister/tegelikudKasusaajad_v2_empty_response.xml"))));

    var result = client.getBeneficialOwners("99000001");

    assertThat(result).isEqualTo(new BeneficialOwners(List.of(), 0));
  }

  private static ResourceSource beneficialOwnersResponsePayload() throws Exception {
    return new ResourceSource(
        new ClassPathResource("ariregister/tegelikudKasusaajad_v2_response.xml"));
  }

  private static ResourceSource responsePayload() throws Exception {
    return new ResourceSource(
        new ClassPathResource("ariregister/ettevottegaSeotudIsikud_v1_response.xml"));
  }

  private static ResourceSource detailandmedResponsePayload() throws Exception {
    return new ResourceSource(new ClassPathResource("ariregister/detailandmed_v2_response.xml"));
  }

  @Configuration
  static class TestConfig {

    @Bean
    Jaxb2Marshaller ariregisterMarshaller() {
      var marshaller = new Jaxb2Marshaller();
      marshaller.setContextPath(
          "ee.tuleva.onboarding.ariregister.generated"
              + ":ee.tuleva.onboarding.ariregister.generated.detailandmed"
              + ":ee.tuleva.onboarding.ariregister.generated.kasusaajad");
      return marshaller;
    }

    @Bean
    WebServiceTemplate ariregisterWebServiceTemplate(Jaxb2Marshaller ariregisterMarshaller) {
      var template = new WebServiceTemplate(ariregisterMarshaller);
      template.setDefaultUri("http://localhost");
      return template;
    }

    @Bean
    AriregisterProperties ariregisterProperties() {
      return new AriregisterProperties("http://localhost", "testuser", "testpass");
    }

    @Bean
    AriregisterClient ariregisterClient(
        WebServiceTemplate ariregisterWebServiceTemplate,
        AriregisterProperties ariregisterProperties) {
      return new AriregisterClient(ariregisterWebServiceTemplate, ariregisterProperties);
    }
  }
}
