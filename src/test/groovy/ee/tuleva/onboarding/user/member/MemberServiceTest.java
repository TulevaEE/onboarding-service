package ee.tuleva.onboarding.user.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.user.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @InjectMocks private MemberService memberService;

  @Test
  void getById_returnsMemberWhenFound() {
    // given
    Long memberId = 1L;
    User user =
        User.builder()
            .id(1L)
            .personalCode("37605030299")
            .firstName("John")
            .lastName("Doe")
            .email("john.doe@example.com")
            .build();

    Member member =
        Member.builder()
            .id(memberId)
            .user(user)
            .memberNumber(1001)
            .createdDate(Instant.now())
            .active(true)
            .build();

    when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

    // when
    Member result = memberService.getById(memberId);

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(memberId);
    assertThat(result.getMemberNumber()).isEqualTo(1001);
    assertThat(result.getActive()).isTrue();
    assertThat(result.getUser().getPersonalCode()).isEqualTo("37605030299");
  }

  @Test
  void getById_throwsExceptionWhenNotFound() {
    // given
    Long memberId = 999L;
    when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> memberService.getById(memberId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Member not found with ID: " + memberId);
  }
}
