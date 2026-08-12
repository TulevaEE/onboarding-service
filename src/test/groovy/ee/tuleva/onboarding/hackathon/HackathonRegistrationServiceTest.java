package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.LOOKING_FOR_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonParticipation.WITH_TEAM;
import static ee.tuleva.onboarding.hackathon.HackathonRole.MENTOR;
import static ee.tuleva.onboarding.hackathon.HackathonRole.PARTICIPANT;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.SOFTWARE_DEVELOPMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class HackathonRegistrationServiceTest {

  private static final Instant DEADLINE = Instant.parse("2026-09-20T20:59:59Z");
  private static final Instant BEFORE_DEADLINE = Instant.parse("2026-08-12T10:00:00Z");
  private static final Instant AFTER_DEADLINE = Instant.parse("2026-09-21T10:00:00Z");

  @Mock private HackathonRegistrationRepository repository;
  @Mock private UserService userService;
  @Mock private HackathonEmailService hackathonEmailService;
  @Mock private LocaleService localeService;

  private final User user = sampleUser().build();
  private final AuthenticatedPerson authenticatedPerson =
      sampleAuthenticatedPersonAndMember().build();

  private HackathonRegistrationService service;

  @BeforeEach
  void setUp() {
    service = serviceAt(BEFORE_DEADLINE);
  }

  private HackathonRegistrationService serviceAt(Instant now) {
    return new HackathonRegistrationService(
        repository,
        userService,
        hackathonEmailService,
        localeService,
        Clock.fixed(now, ZoneOffset.UTC),
        DEADLINE);
  }

  private HackathonRegistrationRequest sampleRequest() {
    return new HackathonRegistrationRequest(
        "participant@example.com",
        "+37255555555",
        PARTICIPANT,
        List.of(SOFTWARE_DEVELOPMENT, DATA_AND_AI),
        List.of(FAIR_LENDING),
        LOOKING_FOR_TEAM,
        "Fondiosaku tagatisel krediidiliin",
        "https://linkedin.com/in/example");
  }

  @Test
  void getRegistration_withoutAnExistingRegistration_prefillsFromTheUserProfile() {
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId())).willReturn(Optional.empty());

    var dto = service.getRegistration(authenticatedPerson);

    assertThat(dto)
        .isEqualTo(
            new HackathonRegistrationDto(
                false,
                true,
                DEADLINE,
                user.getEmail(),
                user.getPhoneNumber(),
                null,
                List.of(),
                List.of(),
                null,
                null,
                null));
  }

  @Test
  void getRegistration_withAnExistingRegistration_returnsIt() {
    var request = sampleRequest();
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId()))
        .willReturn(Optional.of(request.toRegistration(user.getId(), BEFORE_DEADLINE)));

    var dto = service.getRegistration(authenticatedPerson);

    assertThat(dto)
        .isEqualTo(
            new HackathonRegistrationDto(
                true,
                true,
                DEADLINE,
                request.email(),
                request.phoneNumber(),
                request.role(),
                request.skills(),
                request.challenges(),
                request.participation(),
                request.idea(),
                request.linkedinUrl()));
  }

  @Test
  void getRegistration_afterTheDeadline_reportsRegistrationClosed() {
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId())).willReturn(Optional.empty());

    var dto = serviceAt(AFTER_DEADLINE).getRegistration(authenticatedPerson);

    assertThat(dto.open()).isFalse();
    assertThat(dto.deadline()).isEqualTo(DEADLINE);
  }

  @Test
  void register_withoutAnExistingRegistration_savesItAndSendsAConfirmationEmail() {
    var request = sampleRequest();
    var saved = request.toRegistration(user.getId(), BEFORE_DEADLINE);
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId())).willReturn(Optional.empty());
    given(repository.save(any(HackathonRegistration.class))).willReturn(saved);
    given(localeService.getCurrentLocale()).willReturn(Locale.of("et"));

    var dto = service.register(authenticatedPerson, request);

    assertThat(dto.registered()).isTrue();
    assertThat(dto.skills()).containsExactly(SOFTWARE_DEVELOPMENT, DATA_AND_AI);
    verify(repository).save(request.toRegistration(user.getId(), BEFORE_DEADLINE));
    verify(hackathonEmailService).sendRegistrationConfirmation(user, saved, Locale.of("et"));
  }

  @Test
  void register_withAnExistingRegistration_updatesItWithoutSendingAnotherEmail() {
    var existing = sampleRequest().toRegistration(user.getId(), BEFORE_DEADLINE);
    var updated =
        new HackathonRegistrationRequest(
            "updated@example.com",
            null,
            MENTOR,
            List.of(DATA_AND_AI),
            List.of(),
            WITH_TEAM,
            null,
            null);
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId())).willReturn(Optional.of(existing));
    given(repository.save(existing)).willReturn(existing);

    var dto = service.register(authenticatedPerson, updated);

    assertThat(dto)
        .isEqualTo(
            new HackathonRegistrationDto(
                true,
                true,
                DEADLINE,
                "updated@example.com",
                null,
                MENTOR,
                List.of(DATA_AND_AI),
                List.of(),
                WITH_TEAM,
                null,
                null));
    verify(hackathonEmailService, never())
        .sendRegistrationConfirmation(any(), any(), any(Locale.class));
  }

  @Test
  void register_whenAnotherRequestRegisteredFirst_updatesInsteadOfFailing() {
    var request = sampleRequest();
    var concurrentlyCreated = request.toRegistration(user.getId(), BEFORE_DEADLINE);
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId()))
        .willReturn(Optional.empty())
        .willReturn(Optional.of(concurrentlyCreated));
    given(repository.save(any(HackathonRegistration.class)))
        .willThrow(new DataIntegrityViolationException("uq_hackathon_registration_user_id"))
        .willReturn(concurrentlyCreated);

    var dto = service.register(authenticatedPerson, request);

    assertThat(dto.registered()).isTrue();
    verify(hackathonEmailService, never())
        .sendRegistrationConfirmation(any(), any(), any(Locale.class));
  }

  @Test
  void register_atTheDeadlineInstant_isStillAccepted() {
    var request = sampleRequest();
    var saved = request.toRegistration(user.getId(), DEADLINE);
    given(userService.getByIdOrThrow(user.getId())).willReturn(user);
    given(repository.findByUserId(user.getId())).willReturn(Optional.empty());
    given(repository.save(any(HackathonRegistration.class))).willReturn(saved);
    given(localeService.getCurrentLocale()).willReturn(Locale.of("et"));

    var dto = serviceAt(DEADLINE).register(authenticatedPerson, request);

    assertThat(dto.registered()).isTrue();
    assertThat(dto.open()).isTrue();
  }

  @Test
  void register_oneMillisecondAfterTheDeadline_isRejected() {
    var service = serviceAt(DEADLINE.plusMillis(1));

    assertThatThrownBy(() -> service.register(authenticatedPerson, sampleRequest()))
        .isInstanceOf(HackathonRegistrationClosedException.class);
  }

  @Test
  void register_afterTheDeadline_isRejected() {
    var service = serviceAt(AFTER_DEADLINE);

    assertThatThrownBy(() -> service.register(authenticatedPerson, sampleRequest()))
        .isInstanceOf(HackathonRegistrationClosedException.class);

    verifyNoInteractions(repository, hackathonEmailService);
  }
}
