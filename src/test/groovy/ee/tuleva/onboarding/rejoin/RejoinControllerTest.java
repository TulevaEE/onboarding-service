package ee.tuleva.onboarding.rejoin;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.epis.RejoinPermission;
import ee.tuleva.onboarding.epis.RejoinPermissionSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RejoinController.class)
@AutoConfigureMockMvc
class RejoinControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RejoinPermissionSource rejoinPermissionSource;

  @Test
  void tellsTheLoggedInPersonWhetherTheyMayRejoinTheSecondPillar() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    given(rejoinPermissionSource.getRejoinPermission()).willReturn(new RejoinPermission(true));

    mvc.perform(
            get("/v1/rejoin-permission")
                .with(csrf())
                .with(
                    authentication(
                        new UsernamePasswordAuthenticationToken(
                            person, null, List.of(new SimpleGrantedAuthority(USER))))))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"permitted\": true}"));
  }
}
