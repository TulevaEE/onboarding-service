package ee.tuleva.onboarding.auth.event;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.GrantType.ID_CARD;
import static ee.tuleva.onboarding.auth.GrantType.SMART_ID;
import static ee.tuleva.onboarding.auth.idcard.IdCardSession.ID_DOCUMENT_TYPE;
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.ESTONIAN_CITIZEN_ID_CARD;
import static ee.tuleva.onboarding.auth.idcard.IdDocumentType.EUROPEAN_CITIZEN_ID_CARD;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.auth.AuthenticationTokens;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AfterTokenGrantedEventTest {

  private static final AuthenticationTokens TOKENS =
      new AuthenticationTokens("sample-access-token", "sample-refresh-token");

  @Test
  void returnsTheAccessTokenFromTheAuthenticationTokens() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var event = new AfterTokenGrantedEvent(this, person, SMART_ID, TOKENS);

    assertThat(event.getAccessToken()).isEqualTo("sample-access-token");
  }

  @Test
  void isIdCardIsTrueForIdCardGrantType() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.isIdCard()).isTrue();
  }

  @Test
  void isIdCardIsFalseForOtherGrantTypes() {
    var person = sampleAuthenticatedPersonAndMember().build();
    var event = new AfterTokenGrantedEvent(this, person, SMART_ID, TOKENS);

    assertThat(event.isIdCard()).isFalse();
  }

  @Test
  void getIdDocumentTypeReturnsNullWhenPersonHasNoDocumentTypeAttribute() {
    var person = sampleAuthenticatedPersonAndMember().attributes(Map.of()).build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.getIdDocumentType()).isNull();
  }

  @Test
  void getIdDocumentTypeReturnsTheParsedDocumentTypeWhenPresent() {
    var person =
        sampleAuthenticatedPersonAndMember()
            .attributes(Map.of(ID_DOCUMENT_TYPE, ESTONIAN_CITIZEN_ID_CARD.name()))
            .build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.getIdDocumentType()).isEqualTo(ESTONIAN_CITIZEN_ID_CARD);
  }

  @Test
  void isResidentIsNullWhenThereIsNoDocumentType() {
    var person = sampleAuthenticatedPersonAndMember().attributes(Map.of()).build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.isResident()).isNull();
  }

  @Test
  void isResidentIsTrueForAnEstonianCitizenIdCard() {
    var person =
        sampleAuthenticatedPersonAndMember()
            .attributes(Map.of(ID_DOCUMENT_TYPE, ESTONIAN_CITIZEN_ID_CARD.name()))
            .build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.isResident()).isTrue();
  }

  @Test
  void isResidentIsFalseForAEuropeanCitizenIdCard() {
    var person =
        sampleAuthenticatedPersonAndMember()
            .attributes(Map.of(ID_DOCUMENT_TYPE, EUROPEAN_CITIZEN_ID_CARD.name()))
            .build();
    var event = new AfterTokenGrantedEvent(this, person, ID_CARD, TOKENS);

    assertThat(event.isResident()).isFalse();
  }
}
