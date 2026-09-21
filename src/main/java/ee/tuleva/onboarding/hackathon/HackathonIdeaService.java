package ee.tuleva.onboarding.hackathon;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HackathonIdeaService {

  private final HackathonIdeaRepository ideaRepository;
  private final HackathonRegistrationRepository registrationRepository;
  private final Clock clock;
  private final Instant deadline;

  public HackathonIdeaService(
      HackathonIdeaRepository ideaRepository,
      HackathonRegistrationRepository registrationRepository,
      Clock clock,
      @Value("${hackathon.idea-deadline}") Instant deadline) {
    this.ideaRepository = ideaRepository;
    this.registrationRepository = registrationRepository;
    this.clock = clock;
    this.deadline = deadline;
  }

  public HackathonIdeasDto getIdeas(AuthenticatedPerson authenticatedPerson) {
    Long userId = authenticatedPerson.getUserIdOrThrow();
    return new HackathonIdeasDto(
        isOpen(),
        deadline,
        registrationRepository.existsByUserId(userId),
        ideaRepository.findAllByUserIdOrderByCreatedTimeAsc(userId).stream()
            .map(HackathonIdeaDto::from)
            .toList());
  }

  public HackathonIdeaDto submit(
      AuthenticatedPerson authenticatedPerson, HackathonIdeaRequest request) {
    if (!isOpen()) {
      throw new HackathonIdeaSubmissionClosedException(deadline, clock.instant());
    }

    Long userId = authenticatedPerson.getUserIdOrThrow();
    if (!registrationRepository.existsByUserId(userId)) {
      throw new HackathonRegistrationRequiredException(userId);
    }

    HackathonIdea saved = ideaRepository.save(request.toIdea(userId, clock.instant()));
    log.info(
        "Submitted hackathon idea: userId={}, ideaId={}, challenge={}",
        userId,
        saved.getId(),
        saved.getChallenge());
    return HackathonIdeaDto.from(saved);
  }

  private boolean isOpen() {
    return !clock.instant().isAfter(deadline);
  }
}
