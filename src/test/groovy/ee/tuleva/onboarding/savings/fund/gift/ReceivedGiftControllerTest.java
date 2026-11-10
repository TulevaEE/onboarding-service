package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReceivedGiftController.class)
@Import(SecurityConfiguration.class)
class ReceivedGiftControllerTest {

  private static final String CHILD = "61506150006";

  @Autowired private MockMvc mvc;
  @MockitoBean private ReceivedGiftService receivedGiftService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private static Authentication loggedInAs(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void aPersonActingAsThemselvesReadsTheGiftsMadeToThem() throws Exception {
    var child =
        AuthenticatedPerson.builder()
            .personalCode(CHILD)
            .firstName("Mari")
            .lastName("Tamm")
            .role(new Role(PERSON, CHILD, "Mari Tamm"))
            .build();
    given(receivedGiftService.giftsTo(CHILD))
        .willReturn(
            List.of(
                new ReceivedGift(
                    Instant.parse("2026-09-17T10:00:00Z"),
                    new BigDecimal("25.00"),
                    "Leida Tamm",
                    "Palju õnne",
                    true)));

    mvc.perform(get("/v1/savings-fund/gifts").with(authentication(loggedInAs(child))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].receivedAt").value("2026-09-17T10:00:00Z"))
        .andExpect(jsonPath("$[0].amount").value(25.00))
        .andExpect(jsonPath("$[0].giverName").value("Leida Tamm"))
        .andExpect(jsonPath("$[0].message").value("Palju õnne"))
        .andExpect(jsonPath("$[0].confirmed").value(true));
  }

  @Test
  void aParentActingAsTheirChildCannotReadTheChildsGiftsHere() throws Exception {
    var parent =
        sampleAuthenticatedPersonNonMember().role(new Role(PERSON, CHILD, "Mari Tamm")).build();

    mvc.perform(get("/v1/savings-fund/gifts").with(authentication(loggedInAs(parent))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("NOT_ACTING_AS_SELF"));

    verifyNoInteractions(receivedGiftService);
  }

  @Test
  void aBoardMemberActingForACompanyCannotReadGiftsHere() throws Exception {
    var boardMember = sampleAuthenticatedPersonLegalEntity().build();

    mvc.perform(get("/v1/savings-fund/gifts").with(authentication(loggedInAs(boardMember))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("NOT_ACTING_AS_SELF"));

    verifyNoInteractions(receivedGiftService);
  }
}
