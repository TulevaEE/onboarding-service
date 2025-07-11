package ee.tuleva.onboarding.user.member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

  private final MemberRepository memberRepository;

  public Member getById(Long id) {
    return memberRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Member not found with ID: " + id));
  }
}
