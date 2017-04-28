package ee.tuleva.onboarding.user;

import ee.tuleva.onboarding.user.exception.UserAlreadyAMemberException;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
      .memberNumber(generateMemberNumber())
      .build();

    return memberRepository.save(newMember);
  }

  private int generateMemberNumber() {
    Integer maxMemberNumber = memberRepository.getMaxMemberNumber();
    return maxMemberNumber + 1;
  }

}
