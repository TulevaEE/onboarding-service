package ee.tuleva.onboarding.member;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MemberController.class)
@WithMockUser
class MemberControllerTest {

  @Autowired private MockMvc mvc;

  @Autowired private MemberRepository memberRepository;

  @TestConfiguration
  static class MemberControllerTestConfiguration {
    @Bean
    public MemberRepository memberRepository() {
      return Mockito.mock(MemberRepository.class);
    }
  }

  @Test
  void head_gets_total_member_count() throws Exception {
    // given
    given(memberRepository.count()).willReturn(1L);

    // when, then
    mvc.perform(head("/v1/members"))
        .andExpect(status().isOk())
        .andExpect(header().longValue("x-total-count", 1));
  }

  @Test
  void getMemberByPersonalCode_finds_member_by_personal_code() throws Exception {
    // given
    String personalCode = "38501010000";
    String firstName = "Liivi";
    String lastName = "Liige";
    User user =
        User.builder()
            .id(1L)
            .firstName(firstName)
            .lastName(lastName)
            .personalCode(personalCode)
            .build();
    Member member = Member.builder().id(1L).user(user).active(true).memberNumber(123).build();
    given(memberRepository.findByUserPersonalCode(personalCode)).willReturn(Optional.of(member));

    // when, then
    mvc.perform(get("/v1/members/lookup").param("personalCode", personalCode))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/json"))
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.personalCode", is(personalCode)))
        .andExpect(jsonPath("$.firstName", is(firstName)))
        .andExpect(jsonPath("$.lastName", is(lastName)))
        .andExpect(jsonPath("$.memberNumber", is(123)));
  }

  @Test
  void getMemberByPersonalCode_non_active_member() throws Exception {
    // given
    String personalCode = "38501010000";
    String firstName = "Liivi";
    String lastName = "Liige";
    User user =
        User.builder()
            .id(1L)
            .firstName(firstName)
            .lastName(lastName)
            .personalCode(personalCode)
            .build();
    Member member = Member.builder().id(1L).user(user).active(false).memberNumber(123).build();
    given(memberRepository.findByUserPersonalCode(personalCode)).willReturn(Optional.of(member));

    // when, then
    mvc.perform(get("/v1/members/lookup").param("personalCode", personalCode))
        .andExpect(status().isNotFound());
  }

  @Test
  void getMemberByPersonalCode_returns_not_found_when_member_does_not_exist() throws Exception {
    // given
    String personalCode = "38501010000";
    given(memberRepository.findByUserPersonalCode(personalCode)).willReturn(Optional.empty());

    // when, then
    mvc.perform(get("/v1/members/lookup").param("personalCode", personalCode))
        .andExpect(status().isNotFound());
  }
}
