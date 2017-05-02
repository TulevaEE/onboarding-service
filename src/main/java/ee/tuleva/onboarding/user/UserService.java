package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

  private final UserRepository userRepository;
  private final MemberRepository memberRepository;

  User updateUser(String personalCode, String email, String phoneNumber) {
    User user = userRepository.findByPersonalCode(personalCode);
    user.setEmail(email);
    user.setPhoneNumber(phoneNumber);
    return userRepository.save(user);
  }


  public Member registerAsMember(Long userId) {
    User user = userRepository.findOne(userId);

    if(user.getMember().isPresent()) {
      throw new UserAlreadyAMemberException("User is already a member!");
    }

    Member newMember = Member.builder()
      .user(user)
      .memberNumber(memberRepository.getNextMemberNumber())
      .build();

    log.info("Registering user as new member #{}: {}", newMember.getMemberNumber(), user);

    return memberRepository.save(newMember);
  }

  public boolean isAMember(Long userId) {
    User user = userRepository.findOne(userId);
    return user.getMember().isPresent();
  }

}
