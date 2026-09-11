package ee.tuleva.onboarding.hackathon;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class HackathonRegistrationService {

  private final HackathonRegistrationRepository repository;
  private final UserService userService;
  private final HackathonEmailService hackathonEmailService;
  private final LocaleService localeService;
  private final Clock clock;
  private final Instant deadline;

  public HackathonRegistrationService(
      HackathonRegistrationRepository repository,
      UserService userService,
      HackathonEmailService hackathonEmailService,
      LocaleService localeService,
      Clock clock,
      @Value("${hackathon.registration-deadline}") Instant deadline) {
    this.repository = repository;
    this.userService = userService;
    this.hackathonEmailService = hackathonEmailService;
    this.localeService = localeService;
    this.clock = clock;
    this.deadline = deadline;
  }

  public HackathonRegistrationDto getRegistration(AuthenticatedPerson authenticatedPerson) {
    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    return repository
        .findByUserId(user.getIdOrThrow())
        .map(registration -> HackathonRegistrationDto.from(registration, isOpen(), deadline))
        .orElseGet(() -> HackathonRegistrationDto.prefilledFrom(user, isOpen(), deadline));
  }

  public HackathonRegistrationDto register(
      AuthenticatedPerson authenticatedPerson, HackathonRegistrationRequest request) {
    if (!isOpen()) {
      throw new HackathonRegistrationClosedException(deadline, clock.instant());
    }

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());

    return repository
        .findByUserId(user.getIdOrThrow())
        .map(existing -> update(existing, request))
        .orElseGet(() -> createOrUpdateConcurrently(user, request));
  }

  private HackathonRegistrationDto createOrUpdateConcurrently(
      User user, HackathonRegistrationRequest request) {
    try {
      return create(user, request);
    } catch (DataIntegrityViolationException alreadyRegisteredConcurrently) {
      return update(
          repository
              .findByUserId(user.getIdOrThrow())
              .orElseThrow(() -> alreadyRegisteredConcurrently),
          request);
    }
  }

  private HackathonRegistrationDto update(
      HackathonRegistration registration, HackathonRegistrationRequest request) {
    registration.updateFrom(request, clock.instant());
    HackathonRegistration saved = repository.save(registration);
    log.info("Updated hackathon registration: userId={}", saved.getUserId());
    return HackathonRegistrationDto.from(saved, isOpen(), deadline);
  }

  private HackathonRegistrationDto create(User user, HackathonRegistrationRequest request) {
    HackathonRegistration saved =
        repository.save(request.toRegistration(user.getIdOrThrow(), clock.instant()));
    log.info(
        "Created hackathon registration: userId={}, role={}", saved.getUserId(), saved.getRole());
    hackathonEmailService.sendRegistrationConfirmation(
        user, saved, localeService.getCurrentLocale());
    return HackathonRegistrationDto.from(saved, isOpen(), deadline);
  }

  private boolean isOpen() {
    return !clock.instant().isAfter(deadline);
  }
}
