package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.auth.role.Role;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GiftLinkController.class)
@Import(SecurityConfiguration.class)
class GiftLinkControllerTest {

  private static final String CHILD = "61506150006";

  @Autowired private MockMvc mvc;
  @MockitoBean private GiftLinkService giftLinkService;
  @MockitoBean private ReceivedGiftService receivedGiftService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private final AuthenticatedPerson parent =
      sampleAuthenticatedPersonNonMember().role(new Role(PERSON, CHILD, "Mari Maasikas")).build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          parent, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void replacingALinkThatIsNotThereIsNotFound() throws Exception {
    var id = UUID.randomUUID();
    given(giftLinkService.replaceLink(parent.getPersonalCode(), id))
        .willThrow(new NoSuchElementException("No such open gift link: id=" + id));

    mvc.perform(
            post("/v1/savings-fund/gift-links/" + id + "/replace")
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
  }

  @Test
  void openingALinkForSomebodyElsesChildIsForbidden() throws Exception {
    given(giftLinkService.openLinkFor(parent.getPersonalCode(), CHILD))
        .willThrow(new NotAllowedToGiftForException(CHILD));

    mvc.perform(
            post("/v1/savings-fund/gift-links")
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        // The child's personal code must not travel back in the body.
        .andExpect(content().string(""));
  }

  @Test
  void readingTheGiftsOfSomebodyElsesChildIsForbidden() throws Exception {
    willThrow(new NotAllowedToGiftForException(CHILD))
        .given(receivedGiftService)
        .receivedGifts(parent.getPersonalCode(), CHILD);

    mvc.perform(get("/v1/savings-fund/gift-links/gifts").with(authentication(authentication)))
        .andExpect(status().isForbidden())
        .andExpect(content().string(""));
  }
}
