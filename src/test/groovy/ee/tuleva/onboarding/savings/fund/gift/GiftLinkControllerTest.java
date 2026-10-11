package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonLegalEntity;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.savings.fund.gift.GiftLinkFixture.TOKEN;
import static ee.tuleva.onboarding.savings.fund.gift.GiftLinkFixture.anOpenLink;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.jwt.JwtTokenUtil;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.principal.PrincipalService;
import ee.tuleva.onboarding.config.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
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
  private static final String OPEN_FOR_CHILD = "{\"childPersonalCode\":\"" + CHILD + "\"}";

  @Autowired private MockMvc mvc;
  @MockitoBean private GiftLinkService giftLinkService;
  @MockitoBean private ReceivedGiftService receivedGiftService;
  @MockitoBean private JwtTokenUtil jwtTokenUtil;
  @MockitoBean private PrincipalService principalService;

  private final AuthenticatedPerson parent = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication = loggedInAs(parent);
  private final GiftLink link = anOpenLink(CHILD, parent.getPersonalCode());

  private static Authentication loggedInAs(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }

  @Test
  void opensTheLinkForTheChildNamedInTheRequest() throws Exception {
    given(giftLinkService.openLinkFor(parent.getPersonalCode(), CHILD)).willReturn(link);

    mvc.perform(
            post("/v1/savings-fund/gift-links")
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content(OPEN_FOR_CHILD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(link.getId().toString()))
        .andExpect(jsonPath("$.token").value(TOKEN))
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void opensTheLinkWhileActingForACompany() throws Exception {
    var boardMember = sampleAuthenticatedPersonLegalEntity().build();
    given(giftLinkService.openLinkFor(boardMember.getPersonalCode(), CHILD)).willReturn(link);

    mvc.perform(
            post("/v1/savings-fund/gift-links")
                .with(authentication(loggedInAs(boardMember)))
                .contentType(APPLICATION_JSON)
                .content(OPEN_FOR_CHILD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value(TOKEN));
  }

  @Test
  void openingALinkWithoutNamingAChildIsABadRequest() throws Exception {
    mvc.perform(
            post("/v1/savings-fund/gift-links")
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(giftLinkService);
  }

  @Test
  void readsTheGiftsThroughTheLinkTheyWereInvitedWith() throws Exception {
    var receivedAt = Instant.parse("2026-09-17T10:00:00Z");
    given(receivedGiftService.receivedGifts(parent.getPersonalCode(), link.getId()))
        .willReturn(
            List.of(
                new ReceivedGift(receivedAt, new BigDecimal("25.00"), null, "Palju õnne", true)));

    mvc.perform(
            get("/v1/savings-fund/gift-links/" + link.getId() + "/gifts")
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].receivedAt").value("2026-09-17T10:00:00Z"))
        .andExpect(jsonPath("$[0].amount").value(25.00))
        .andExpect(jsonPath("$[0].giverName").value((Object) null))
        .andExpect(jsonPath("$[0].message").value("Palju õnne"))
        .andExpect(jsonPath("$[0].confirmed").value(true));
  }

  @Test
  void readingTheGiftsThroughALinkThatIsNotThereIsNotFound() throws Exception {
    var id = UUID.randomUUID();
    willThrow(new NoSuchElementException("No such gift link: id=" + id))
        .given(receivedGiftService)
        .receivedGifts(parent.getPersonalCode(), id);

    mvc.perform(
            get("/v1/savings-fund/gift-links/" + id + "/gifts")
                .with(authentication(authentication)))
        .andExpect(status().isNotFound())
        .andExpect(content().string(""));
  }

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
        .willThrow(new NotAllowedToGiftForException(parent.getPersonalCode(), CHILD));

    mvc.perform(
            post("/v1/savings-fund/gift-links")
                .with(authentication(authentication))
                .contentType(APPLICATION_JSON)
                .content(OPEN_FOR_CHILD))
        .andExpect(status().isForbidden())
        // The child's personal code must not travel back in the body.
        .andExpect(content().string(""));
  }

  @Test
  void readingTheGiftsOfSomebodyElsesChildIsForbidden() throws Exception {
    willThrow(new NotAllowedToGiftForException(parent.getPersonalCode(), CHILD))
        .given(receivedGiftService)
        .receivedGifts(parent.getPersonalCode(), link.getId());

    mvc.perform(
            get("/v1/savings-fund/gift-links/" + link.getId() + "/gifts")
                .with(authentication(authentication)))
        .andExpect(status().isForbidden())
        .andExpect(content().string(""));
  }
}
