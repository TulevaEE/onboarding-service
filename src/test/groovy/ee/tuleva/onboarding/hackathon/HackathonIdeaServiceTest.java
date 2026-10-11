package ee.tuleva.onboarding.hackathon;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.hackathon.HackathonChallenge.FAIR_LENDING;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DATA_AND_AI;
import static ee.tuleva.onboarding.hackathon.HackathonSkill.DESIGN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HackathonIdeaServiceTest {

  private static final Instant DEADLINE = Instant.parse("2026-09-30T20:59:59Z");
  private static final Instant BEFORE_DEADLINE = Instant.parse("2026-09-15T10:00:00Z");
  private static final Instant AFTER_DEADLINE = Instant.parse("2026-10-01T10:00:00Z");

  @Mock private HackathonIdeaRepository ideaRepository;
  @Mock private HackathonRegistrationRepository registrationRepository;

  private final AuthenticatedPerson authenticatedPerson =
      sampleAuthenticatedPersonAndMember().build();
  private final Long userId = authenticatedPerson.getUserIdOrThrow();

  private HackathonIdeaService serviceAt(Instant now) {
    return new HackathonIdeaService(
        ideaRepository, registrationRepository, Clock.fixed(now, ZoneOffset.UTC), DEADLINE);
  }

  private HackathonIdeaRequest sampleRequest() {
    return new HackathonIdeaRequest(
        FAIR_LENDING,
        "Laenu vahetamine on tülikas",
        "Fondiosaku tagatisel krediidiliin",
        null,
        List.of(DESIGN, DATA_AND_AI),
        null);
  }

  @Test
  void getIdeas_returnsMyIdeasAndWhetherIHaveRegistered() {
    var idea = sampleRequest().toIdea(userId, BEFORE_DEADLINE);
    idea.setId(1L);
    given(registrationRepository.existsByUserId(userId)).willReturn(true);
    given(ideaRepository.findAllByUserIdOrderByCreatedTimeAsc(userId)).willReturn(List.of(idea));

    var dto = serviceAt(BEFORE_DEADLINE).getIdeas(authenticatedPerson);

    assertThat(dto)
        .isEqualTo(
            new HackathonIdeasDto(true, DEADLINE, true, List.of(HackathonIdeaDto.from(idea))));
  }

  @Test
  void getIdeas_afterTheDeadline_reportsSubmissionClosed() {
    given(registrationRepository.existsByUserId(userId)).willReturn(false);
    given(ideaRepository.findAllByUserIdOrderByCreatedTimeAsc(userId)).willReturn(List.of());

    var dto = serviceAt(AFTER_DEADLINE).getIdeas(authenticatedPerson);

    assertThat(dto).isEqualTo(new HackathonIdeasDto(false, DEADLINE, false, List.of()));
  }

  @Test
  void submit_whenRegistered_savesTheIdea() {
    var request = sampleRequest();
    var saved = request.toIdea(userId, BEFORE_DEADLINE);
    saved.setId(7L);
    given(registrationRepository.existsByUserId(userId)).willReturn(true);
    given(ideaRepository.save(request.toIdea(userId, BEFORE_DEADLINE))).willReturn(saved);

    var dto = serviceAt(BEFORE_DEADLINE).submit(authenticatedPerson, request);

    assertThat(dto).isEqualTo(HackathonIdeaDto.from(saved));
  }

  @Test
  void submit_withoutARegistration_isRejected() {
    given(registrationRepository.existsByUserId(userId)).willReturn(false);
    var service = serviceAt(BEFORE_DEADLINE);

    assertThatThrownBy(() -> service.submit(authenticatedPerson, sampleRequest()))
        .isInstanceOf(HackathonRegistrationRequiredException.class);

    verify(ideaRepository, never()).save(any());
  }

  @Test
  void submit_atTheDeadlineInstant_isStillAccepted() {
    var request = sampleRequest();
    var saved = request.toIdea(userId, DEADLINE);
    saved.setId(8L);
    given(registrationRepository.existsByUserId(userId)).willReturn(true);
    given(ideaRepository.save(request.toIdea(userId, DEADLINE))).willReturn(saved);

    var dto = serviceAt(DEADLINE).submit(authenticatedPerson, request);

    assertThat(dto).isEqualTo(HackathonIdeaDto.from(saved));
  }

  @Test
  void submit_afterTheDeadline_isRejected() {
    var service = serviceAt(AFTER_DEADLINE);

    assertThatThrownBy(() -> service.submit(authenticatedPerson, sampleRequest()))
        .isInstanceOf(HackathonIdeaSubmissionClosedException.class);

    verifyNoInteractions(ideaRepository, registrationRepository);
  }
}
