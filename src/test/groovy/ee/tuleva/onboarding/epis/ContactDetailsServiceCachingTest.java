package ee.tuleva.onboarding.epis;

import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.ResponseEntity.ok;

import ee.tuleva.onboarding.auth.ServiceTokenProvider;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.config.CacheConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.RestTemplate;

@SpringJUnitConfig(
    classes = {
      ContactDetailsService.class,
      EpisService.class,
      EpisRequestHeaders.class,
      CacheConfiguration.class
    })
@TestPropertySource(
    properties = {"epis.service.url=http://epis", "epis.service.long-request-url=http://epis-long"})
class ContactDetailsServiceCachingTest {

  private static final String CONTACT_DETAILS_URL = "http://epis/contact-details";
  private static final String TOKEN = "user-token";

  @MockitoBean(name = "episRestTemplate")
  private RestTemplate episRestTemplate;

  @MockitoBean(name = "episLongRequestRestTemplate")
  private RestTemplate episLongRequestRestTemplate;

  @MockitoBean private ServiceTokenProvider serviceTokenProvider;

  @Autowired private ContactDetailsService contactDetailsService;

  private final Person person = samplePerson();

  @AfterEach
  void tearDown() {
    contactDetailsService.clearCache(person);
    SecurityContextHolder.clearContext();
  }

  @Test
  void servesContactDetailsWithoutAPensionAccountNumberFromTheCacheOnTheNextRead() {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("person", TOKEN));
    ContactDetails withoutPensionAccount = withoutPensionAccount();
    givenTheRegistryAnswers(withoutPensionAccount, contactDetailsFixture());

    ContactDetails first = contactDetailsService.getContactDetails(person);
    ContactDetails second = contactDetailsService.getContactDetails(person);

    assertThat(first).isSameAs(withoutPensionAccount);
    assertThat(second).isSameAs(withoutPensionAccount);
    thenTheRegistryWasAskedOnce();
  }

  @Test
  void servesContactDetailsWithoutAPensionAccountNumberFromTheCacheOnTheNextReadForAGivenToken() {
    ContactDetails withoutPensionAccount = withoutPensionAccount();
    givenTheRegistryAnswers(withoutPensionAccount, contactDetailsFixture());

    ContactDetails first = contactDetailsService.getContactDetails(person, TOKEN);
    ContactDetails second = contactDetailsService.getContactDetails(person, TOKEN);

    assertThat(first).isSameAs(withoutPensionAccount);
    assertThat(second).isSameAs(withoutPensionAccount);
    thenTheRegistryWasAskedOnce();
  }

  @Test
  void asksTheRegistryAgainOnceTheCacheIsCleared() {
    ContactDetails beforeClearing = withoutPensionAccount();
    ContactDetails afterClearing = contactDetailsFixture();
    givenTheRegistryAnswers(beforeClearing, afterClearing);

    ContactDetails first = contactDetailsService.getContactDetails(person, TOKEN);
    contactDetailsService.clearCache(person);
    ContactDetails second = contactDetailsService.getContactDetails(person, TOKEN);

    assertThat(first).isSameAs(beforeClearing);
    assertThat(second).isSameAs(afterClearing);
  }

  private void givenTheRegistryAnswers(ContactDetails first, ContactDetails then) {
    given(
            episRestTemplate.exchange(
                eq(CONTACT_DETAILS_URL), eq(GET), any(HttpEntity.class), eq(ContactDetails.class)))
        .willReturn(ok(first), ok(then));
  }

  private void thenTheRegistryWasAskedOnce() {
    then(episRestTemplate)
        .should()
        .exchange(
            eq(CONTACT_DETAILS_URL), eq(GET), any(HttpEntity.class), eq(ContactDetails.class));
  }

  private static ContactDetails withoutPensionAccount() {
    ContactDetails contactDetails = contactDetailsFixture();
    contactDetails.setPensionAccountNumber(null);
    return contactDetails;
  }
}
