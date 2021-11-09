package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import java.util.Optional;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final MemberRepository memberRepository;

  // TODO: replace with Optional<User>
  @Nullable
  public User getById(Long userId) {
    return userRepository.findById(userId).orElse(null);
  }

  public Optional<User> findByPersonalCode(String personalCode) {
    return userRepository.findByPersonalCode(personalCode);
  }

  public User createNewUser(User user) {
    log.info("Creating new user for personal code {}", user.getPersonalCode());
    return userRepository.save(user);
  }

  public User updateUser(String personalCode, String email, String phoneNumber) {
    User user =
        findByPersonalCode(personalCode)
            .map(
                existingUser -> {
                  existingUser.setEmail(email);
                  existingUser.setPhoneNumber(phoneNumber);
                  return existingUser;
                })
            .orElseThrow(() -> new RuntimeException("User does not exist"));

    log.info("Updating user: userId={}", user.getId());

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

    return save(user);
  }

  public boolean isAMember(Long userId) {
    Optional<User> user = userRepository.findById(userId);
    return user.map(u -> u.getMember().isPresent()).orElse(false);
  }

  public User save(User user) {
    log.info("Saving user: userId={}", user.getId());
    return userRepository.save(user);
  }

  public boolean isEmailExist(String personalCode, String email) {
    Optional<User> existingUser = userRepository.findByEmail(email);
    return existingUser.isPresent() && !personalCode.equals(existingUser.get().getPersonalCode());
  }
}
