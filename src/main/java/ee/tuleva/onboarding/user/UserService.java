package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.notification.mailchimp.MailChimpService;
import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final MemberRepository memberRepository;
  private final MailChimpService mailChimpService;

  public User getById(Long userId) {
    return userRepository.findOne(userId);
  }

  public User findByPersonalCode(String personalCode) {
    return userRepository.findByPersonalCode(personalCode);
  }

  public User createNewUser(User user) {
    return userRepository.save(user);
  }

  public User updateUser(String personalCode, String email, String phoneNumber) {
    User user = findByPersonalCode(personalCode);
    user.setEmail(email);
    user.setPhoneNumber(phoneNumber);
    return updateUser(user);
  }

  public User registerAsMember(Long userId) {
    User user = userRepository.findOne(userId);

    if(user.getMember().isPresent()) {
      throw new UserAlreadyAMemberException("User is already a member!");
    }

    Member newMember = Member.builder()
      .user(user)
      .memberNumber(memberRepository.getNextMemberNumber())
      .build();

    log.info("Registering user as new member #{}: {}", newMember.getMemberNumber(), user);

    user.setMember(newMember);
    return updateUser(user);
  }

  public boolean isAMember(Long userId) {
    User user = userRepository.findOne(userId);
    return user.getMember().isPresent();
  }

  private User updateUser(User user) {
    User savedUser = userRepository.save(user);
    mailChimpService.createOrUpdateMember(savedUser);
    return savedUser;
  }
}
