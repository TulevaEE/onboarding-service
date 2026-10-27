package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SavingsFundAccountClosureController.class)
@AutoConfigureMockMvc
@WithMockUser
class SavingsFundAccountClosureControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private SavingsFundAccountClosureService closureService;

  private final AuthenticatedPerson person = sampleAuthenticatedPersonAndMember().build();
  private final UsernamePasswordAuthenticationToken auth =
      new UsernamePasswordAuthenticationToken(
          person, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void getAccountClosure_tellsWhetherTheAccountCanBeClosed() throws Exception {
    given(closureService.isClosable(person)).willReturn(true);

    mvc.perform(get("/v1/savings/account-closure").with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"closable\": true}"));
  }

  @Test
  void closeAccount_closesTheAccount() throws Exception {
    mvc.perform(post("/v1/savings/account-closure").with(csrf()).with(authentication(auth)))
        .andExpect(status().isNoContent());

    verify(closureService).close(person);
  }

  @Test
  void closeAccount_whenRefused_returnsConflict() throws Exception {
    willThrow(
            new SavingsFundAccountClosureRefusedException("Savings fund account cannot be closed"))
        .given(closureService)
        .close(person);

    mvc.perform(post("/v1/savings/account-closure").with(csrf()).with(authentication(auth)))
        .andExpect(status().isConflict());
  }
}
