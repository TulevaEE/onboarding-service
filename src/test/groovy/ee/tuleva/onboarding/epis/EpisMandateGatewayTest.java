package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.application.ApplicationSnapshotFixture.sampleTransferApplicationDto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.mandate.LegacyMandateSubmission;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EpisMandateGatewayTest {

  @Mock EpisService episService;
  @Mock ContactDetailsService contactDetailsService;

  EpisMandateGateway gateway;
  EpisMandateContacts contacts;

  @BeforeEach
  void setUp() {
    gateway = new EpisMandateGateway(episService);
    contacts = new EpisMandateContacts(contactDetailsService);
  }

  @Test
  void delegatesGetApplications() {
    Person person = samplePerson();
    List<ApplicationSnapshot> applications = List.of(sampleTransferApplicationDto());
    given(episService.getApplications(person)).willReturn(applications);

    assertThat(gateway.getApplications(person)).isEqualTo(applications);
  }

  @Test
  void delegatesAndMapsGetContactDetails() {
    Person person = samplePerson();
    given(contactDetailsService.getContactDetails(person)).willReturn(contactDetailsFixture());

    var result = contacts.getContactDetails(person);

    assertThat(result.email()).isEqualTo("tuleva@tuleva.ee");
    assertThat(result.secondPillarActive()).isTrue();
  }

  @Test
  void delegatesUpdateContactDetails() {
    Person person = samplePerson();
    Country address = Country.builder().countryCode("EE").build();

    contacts.updateContactDetails(person, "email@tuleva.ee", "+372555", address);

    verify(contactDetailsService)
        .updateContactDetails(person, "email@tuleva.ee", "+372555", address);
  }

  @Test
  void delegatesClearCache() {
    Person person = samplePerson();

    contacts.clearCache(person);

    verify(contactDetailsService).clearCache(person);
  }

  @Test
  void delegatesSendMandateV2() {
    MandateSubmissionCommand<?> command =
        MandateSubmissionCommand.builder().processId("processId").submission(null).build();
    var result = MandateProcessResult.builder().outcomes(List.of()).build();
    given(episService.sendMandateV2(command)).willReturn(result);

    assertThat(gateway.sendMandateV2(command)).isEqualTo(result);
  }

  @Test
  void delegatesSendMandate() {
    LegacyMandateSubmission submission =
        LegacyMandateSubmission.builder()
            .id(1L)
            .createdDate(Instant.now())
            .pillar(2)
            .fundTransferExchanges(List.of())
            .email("email@tuleva.ee")
            .phoneNumber("+372555")
            .build();
    var result = MandateProcessResult.builder().outcomes(List.of()).build();
    given(episService.sendMandate(submission)).willReturn(result);

    assertThat(gateway.sendMandate(submission)).isEqualTo(result);
  }
}
