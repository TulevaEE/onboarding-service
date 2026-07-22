package ee.tuleva.onboarding.kyc.survey;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.kyc.survey.KycSurveyResponseItem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.country.Country;
import ee.tuleva.onboarding.kyc.KycCheckService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KycSurveyServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");
  private static final Long USER_ID = 1L;

  @Mock private KycSurveyRepository kycSurveyRepository;
  @Mock private KycCheckService kycCheckService;
  @Mock private UserService userService;

  @InjectMocks private KycSurveyService kycSurveyService;

  private final User user =
      User.builder().id(USER_ID).email("test@example.com").phoneNumber("+37255555555").build();

  @BeforeEach
  void setUp() {
    ClockHolder.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  void submit_storesSurveyUnderActiveRoleSubjectAndRunsCheckOnSubject() {
    var childCode = "61506150006";
    var person =
        sampleAuthenticatedPersonNonMember()
            .role(new Role(RoleType.PERSON, childCode, "Child Person"))
            .build();
    var subject = User.builder().id(7L).personalCode(childCode).build();
    var survey =
        identitySurvey(
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))));
    given(userService.findByPersonalCode(childCode)).willReturn(Optional.of(subject));
    given(kycSurveyRepository.saveAndFlush(any(KycSurvey.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var saved = kycSurveyService.submit(person, survey);

    assertThat(saved.getSurvey()).isEqualTo(survey);
    assertThat(saved.getUserId()).isEqualTo(subject.getId());
    verify(kycCheckService).check(subject, new Country("EE"), survey.purpose());
  }

  @Test
  void submit_throwsWhenActiveRoleSubjectNotFound() {
    var childCode = "61506150006";
    var person =
        sampleAuthenticatedPersonNonMember()
            .role(new Role(RoleType.PERSON, childCode, "Child Person"))
            .build();
    var survey =
        identitySurvey(
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))));
    given(userService.findByPersonalCode(childCode)).willReturn(Optional.empty());

    assertThatThrownBy(() -> kycSurveyService.submit(person, survey))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void submit_whenActingAsCompany_storesSurveyUnderRepresentativeAndRunsCheckOnThem() {
    var person = sampleAuthenticatedPersonLegalEntity().build();
    var subject = User.builder().id(7L).personalCode(person.getPersonalCode()).build();
    var survey =
        identitySurvey(
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))));
    given(userService.findByPersonalCode(person.getPersonalCode()))
        .willReturn(Optional.of(subject));
    given(kycSurveyRepository.saveAndFlush(any(KycSurvey.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    var saved = kycSurveyService.submit(person, survey);

    assertThat(saved.getSurvey()).isEqualTo(survey);
    assertThat(saved.getUserId()).isEqualTo(subject.getId());
    verify(kycCheckService).check(subject, new Country("EE"), survey.purpose());
  }

  @Test
  void getIdentity_whenActingAsCompany_returnsRepresentativeIdentity() {
    var person = sampleAuthenticatedPersonLegalEntity().build();
    given(userService.findByPersonalCode(person.getPersonalCode())).willReturn(Optional.of(user));
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.empty());

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(null, null, "test@example.com", "+37255555555", null, null));
  }

  @Test
  void getCountry_returnsCountryFromAddressAnswer() {
    Long userId = 1L;
    var addressDetails = new AddressDetails("Street 1", "Tallinn", "12345", "EE");
    var addressValue = new AddressValue("ADDRESS", addressDetails);
    var addressItem = new Address(addressValue);
    var survey = new KycSurveyResponse(List.of(addressItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .willReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).contains(new Country("EE"));
  }

  @Test
  void getCountry_returnsEmptyWhenNoSurveyFound() {
    Long userId = 1L;

    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .willReturn(Optional.empty());

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void getCountry_returnsEmptyWhenSurveyHasNoAddressAnswer() {
    Long userId = 1L;
    var emailValue = new EmailValue("TEXT", "test@example.com");
    var emailItem = new Email(emailValue);
    var survey = new KycSurveyResponse(List.of(emailItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .willReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).isEmpty();
  }

  @Test
  void getIdentity_returnsUserContactDetailsAndIncompleteWhenNoSurveyExists() {
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.empty());

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(null, null, "test@example.com", "+37255555555", null, null));
    assertThat(identity.isComplete()).isFalse();
  }

  @Test
  void getIdentity_combinesLatestSurveyWithUserContactDetails() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE", "FI"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE", "FI"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "test@example.com",
                "+37255555555",
                PepStatus.IS_NOT_PEP,
                createdTime));
    assertThat(identity.isComplete()).isTrue();
  }

  @Test
  void getIdentity_withoutPepDeclaration_isIncomplete() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))));
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "test@example.com",
                "+37255555555",
                null,
                createdTime));
    assertThat(identity.isComplete()).isFalse();
  }

  @Test
  void getIdentity_withoutUserEmail_isIncomplete() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(User.builder().id(USER_ID).build());
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                null,
                null,
                PepStatus.IS_NOT_PEP,
                createdTime));
    assertThat(identity.isComplete()).isFalse();
  }

  @Test
  void getIdentity_prefersSurveyContactDetailsOverUserRecord() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new Email(new EmailValue("TEXT", "founder@example.com")),
            new PhoneNumber(new TextValue("TEXT", "+37211111111")),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "founder@example.com",
                "+37211111111",
                PepStatus.IS_NOT_PEP,
                createdTime));
    assertThat(identity.isComplete()).isTrue();
  }

  @Test
  void getIdentity_completesFromSurveyEmailWhenUserRecordHasNoContactDetails() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new Email(new EmailValue("TEXT", "founder@example.com")),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(User.builder().id(USER_ID).build());
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "founder@example.com",
                null,
                PepStatus.IS_NOT_PEP,
                createdTime));
    assertThat(identity.isComplete()).isTrue();
  }

  @Test
  void getIdentity_treatsBlankSurveyContactAnswersAsAbsent() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new Email(new EmailValue("TEXT", " ")),
            new PhoneNumber(new TextValue("TEXT", "")),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "test@example.com",
                "+37255555555",
                PepStatus.IS_NOT_PEP,
                createdTime));
  }

  @Test
  void getIdentity_blankSurveyEmailWithoutUserEmailStaysIncomplete() {
    var createdTime = Instant.parse("2026-06-01T10:00:00Z");
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new Email(new EmailValue("TEXT", "")),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(User.builder().id(USER_ID).build());
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity.email()).isNull();
    assertThat(identity.isComplete()).isFalse();
  }

  @Test
  void getIdentity_returnsSurveyOlderThanAYearForPrefillButIncomplete() {
    var createdTime = NOW.minus(Duration.ofDays(366));
    var survey =
        identitySurvey(
            new Citizenship(new CountriesValue("COUNTRIES", List.of("EE"))),
            new Address(
                new AddressValue(
                    "ADDRESS", new AddressDetails("Street 1", "Tallinn", "12345", "EE"))),
            new PepSelfDeclaration(new OptionValue<>("OPTION", PepStatus.IS_NOT_PEP)));
    var person = personResolvingTo(user);
    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(USER_ID))
        .willReturn(Optional.of(kycSurvey(USER_ID, survey, createdTime)));

    var identity = kycSurveyService.getIdentity(person);

    assertThat(identity)
        .isEqualTo(
            new KycIdentityResponse(
                List.of("EE"),
                new KycIdentityResponse.Address("Street 1", "Tallinn", "12345", "EE"),
                "test@example.com",
                "+37255555555",
                PepStatus.IS_NOT_PEP,
                createdTime));
    assertThat(identity.isComplete()).isFalse();
  }

  private AuthenticatedPerson personResolvingTo(User subject) {
    var person = sampleAuthenticatedPersonNonMember().build();
    given(userService.findByPersonalCode(person.getRoleCode())).willReturn(Optional.of(subject));
    return person;
  }

  private KycSurveyResponse identitySurvey(KycSurveyResponseItem... answers) {
    return new KycSurveyResponse(List.of(answers));
  }

  private KycSurvey kycSurvey(Long userId, KycSurveyResponse survey, Instant createdTime) {
    return KycSurvey.builder().userId(userId).survey(survey).createdTime(createdTime).build();
  }

  @Test
  void getCountry_returnsFirstAddressWhenMultipleAnswersExist() {
    Long userId = 1L;
    var emailValue = new EmailValue("TEXT", "test@example.com");
    var emailItem = new Email(emailValue);
    var addressDetails = new AddressDetails("Street 1", "Helsinki", "00100", "FI");
    var addressValue = new AddressValue("ADDRESS", addressDetails);
    var addressItem = new Address(addressValue);
    var survey = new KycSurveyResponse(List.of(emailItem, addressItem));
    var kycSurvey = KycSurvey.builder().userId(userId).survey(survey).build();

    given(kycSurveyRepository.findFirstByUserIdOrderByCreatedTimeDesc(userId))
        .willReturn(Optional.of(kycSurvey));

    Optional<Country> result = kycSurveyService.getCountry(userId);

    assertThat(result).contains(new Country("FI"));
  }
}
