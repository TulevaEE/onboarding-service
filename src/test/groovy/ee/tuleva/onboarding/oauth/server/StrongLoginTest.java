package ee.tuleva.onboarding.oauth.server;

import static ee.tuleva.onboarding.auth.role.RoleType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StrongLoginTest {

  private static final String PERSONAL_CODE = "38812121215";
  private static final Instant REQUEST_CREATED = Instant.parse("2026-10-08T09:00:00.700Z");

  @ParameterizedTest
  @ValueSource(strings = {"SMART_ID", "MOBILE_ID", "ID_CARD"})
  void aSmartIdMobileIdOrIdCardLoginAfterTheRequestCounts(String grantType) {
    var person = person(grantType, "2026-10-08T09:01:00Z", ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isTrue();
  }

  @Test
  void aPartnerHandoverLoginDoesNotCount() {
    var person = person("PARTNER", "2026-10-08T09:01:00Z", ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  @Test
  void aLoginWithoutAMethodDoesNotCount() {
    var person = person(null, "2026-10-08T09:01:00Z", ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  @Test
  void aLoginFromBeforeTheRequestDoesNotCount() {
    var person = person("SMART_ID", "2026-10-08T08:59:59Z", ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  @Test
  void aLoginInTheSameSecondAsTheRequestCountsBecauseTheLoginTimeIsRecordedToTheSecond() {
    var person = person("SMART_ID", "2026-10-08T09:00:00Z", ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isTrue();
  }

  @Test
  void aContextWithoutALoginTimeDoesNotCount() {
    var person = person("SMART_ID", null, ownRole());

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  @Test
  void actingForACompanyDoesNotCount() {
    var person =
        person("SMART_ID", "2026-10-08T09:01:00Z", new Role(LEGAL_ENTITY, "12345678", "Firma OÜ"));

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  @Test
  void actingForAChildDoesNotCount() {
    var person =
        person("SMART_ID", "2026-10-08T09:01:00Z", new Role(PERSON, "60001019906", "Laps"));

    assertThat(StrongLogin.asThemselvesSince(person, REQUEST_CREATED)).isFalse();
  }

  private static Role ownRole() {
    return new Role(PERSON, PERSONAL_CODE, "Jordan Valdma");
  }

  private static AuthenticatedPerson person(String grantType, String authTime, Role role) {
    var attributes = new HashMap<String, String>();
    if (grantType != null) {
      attributes.put("grantType", grantType);
    }
    if (authTime != null) {
      attributes.put("authTime", authTime);
    }
    return AuthenticatedPerson.builder()
        .personalCode(PERSONAL_CODE)
        .firstName("Jordan")
        .lastName("Valdma")
        .attributes(Map.copyOf(attributes))
        .role(role)
        .build();
  }
}
