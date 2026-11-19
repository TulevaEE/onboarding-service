package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.user.contact.ContactChangeRecorder;
import ee.tuleva.onboarding.user.exception.DuplicateEmailException;
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import ee.tuleva.onboarding.user.name.NameChangeRecorder;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final MemberRepository memberRepository;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final ContactChangeRecorder contactChangeRecorder;
  private final NameChangeRecorder nameChangeRecorder;

  public Optional<User> getById(Long userId) {
    return userRepository.findById(userId);
  }

  public User getByIdOrThrow(Long userId) {
    return userRepository.findById(userId).orElseThrow();
  }

  public Optional<User> findByPersonalCode(String personalCode) {
    return userRepository.findByPersonalCode(personalCode);
  }

  public User createNewUser(User user) {
    log.info("Creating new user");
    try {
      return userRepository.save(user);
    } catch (DataIntegrityViolationException e) {
      User existing =
          userRepository.findByPersonalCode(user.getPersonalCode()).orElseThrow(() -> e);
      log.info(
          "Personal code already taken by a concurrent login, reusing it: userId={}",
          existing.getId());
      return existing;
    }
  }

  @Transactional
  public User changeContactDetails(
      String personalCode, String email, @Nullable String phoneNumber) {
    User user = lockedUser(personalCode);
    String oldEmail = user.getEmail();
    String oldPhoneNumber = user.getPhoneNumber();
    throwIfEmailTaken(personalCode, Optional.of(email));
    User savedUser = setContactDetails(user, email, phoneNumber);
    contactChangeRecorder.recordSelfServiceChange(savedUser, oldEmail, oldPhoneNumber);
    return savedUser;
  }

  @Transactional
  public User fillMissingContactDetails(
      String personalCode, Optional<String> email, @Nullable String phoneNumber) {
    User user = lockedUser(personalCode);
    if (user.hasContactDetails()) {
      log.info("User contact details already filled in, leaving them: userId={}", user.getId());
      return user;
    }
    throwIfEmailTaken(personalCode, email);
    return setContactDetails(user, email.orElse(null), phoneNumber);
  }

  @Transactional
  public User createNewUser(User user, NameChangeBasis nameBasis) {
    User createdUser = createNewUser(user);
    nameChangeRecorder.recordFirst(createdUser, nameBasis);
    return createdUser;
  }

  @Transactional
  public void changeName(
      String personalCode, String firstName, String lastName, NameChangeBasis basis) {
    userRepository
        .findByPersonalCode(personalCode)
        .filter(user -> !hasName(user, firstName, lastName))
        .flatMap(user -> userRepository.lockForUpdate(personalCode))
        .filter(lockedUser -> !hasName(lockedUser, firstName, lastName))
        .ifPresent(lockedUser -> rename(lockedUser, firstName, lastName, basis));
  }

  private static boolean hasName(User user, String firstName, String lastName) {
    return Objects.equals(user.getFirstName(), firstName)
        && Objects.equals(user.getLastName(), lastName);
  }

  private void rename(User user, String firstName, String lastName, NameChangeBasis basis) {
    String oldFirstName = user.getFirstName();
    String oldLastName = user.getLastName();
    user.setFirstName(firstName);
    user.setLastName(lastName);
    log.info("Changing user name: userId={}, source={}", user.getId(), basis.source());
    nameChangeRecorder.record(save(user), oldFirstName, oldLastName, basis);
  }

  private User lockedUser(String personalCode) {
    return userRepository.lockForUpdate(personalCode).orElseThrow(UserService::userDoesNotExist);
  }

  private void throwIfEmailTaken(String personalCode, Optional<String> email) {
    if (isExistingEmail(personalCode, email)) {
      throw DuplicateEmailException.newInstance();
    }
  }

  private static RuntimeException userDoesNotExist() {
    return new RuntimeException("User does not exist");
  }

  private User setContactDetails(User user, @Nullable String email, @Nullable String phoneNumber) {
    user.setEmail(email);
    user.setPhoneNumber(phoneNumber);
    log.info("Updating user contact details: userId={}", user.getId());
    return save(user);
  }

  public User registerAsMember(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalStateException("No user found"));

    if (user.getMember().isPresent()) {
      throw new UserAlreadyAMemberException("User is already a member! " + userId);
    }

    Member newMember =
        Member.builder().user(user).memberNumber(memberRepository.getNextMemberNumber()).build();

    log.info(
        "Registering user as new member: member #={}, userId={}",
        newMember.getMemberNumber(),
        user.getId());

    user.setMember(newMember);

    User savedUser = save(user);
    applicationEventPublisher.publishEvent(new MemberCreatedEvent(user));

    return savedUser;
  }

  public boolean isAMember(Long userId) {
    Optional<User> user = userRepository.findById(userId);
    return user.map(u -> u.getMember().isPresent()).orElse(false);
  }

  public User save(User user) {
    log.info("Saving user: userId={}", user.getId());
    return userRepository.save(user);
  }

  public boolean isExistingEmail(String personalCode, Optional<String> email) {
    if (email.isEmpty()) return false;

    Optional<User> existingUser = userRepository.findByEmail(email.get());
    return existingUser.isPresent() && !personalCode.equals(existingUser.get().getPersonalCode());
  }

  public User getByMemberId(Long memberId) {
    return userRepository
        .findByMember_Id(memberId)
        .orElseThrow(() -> new IllegalStateException("No user found: memberId=" + memberId));
  }
}
