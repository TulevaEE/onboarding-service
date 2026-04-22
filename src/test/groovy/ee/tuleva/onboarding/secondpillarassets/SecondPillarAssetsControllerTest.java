package ee.tuleva.onboarding.secondpillarassets;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.secondpillarassets.SecondPillarAssetsFixture.secondPillarAssetsFixture;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.epis.EpisService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SecondPillarAssetsController.class)
@AutoConfigureMockMvc
class SecondPillarAssetsControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private EpisService episService;

  @Test
  void getSecondPillarAssets_returnsBreakdownForAuthenticatedUser() throws Exception {
    var person = sampleAuthenticatedPersonAndMember().build();
    var auth =
        new UsernamePasswordAuthenticationToken(
            person, null, List.of(new SimpleGrantedAuthority(USER)));
    SecondPillarAssets assets = secondPillarAssetsFixture();
    given(episService.getSecondPillarAssets(person)).willReturn(assets);

    mvc.perform(get("/v1/second-pillar-assets").with(csrf()).with(authentication(auth)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.balance", is(assets.balance().doubleValue())))
        .andExpect(
            jsonPath(
                "$.employeeWithheldPortion", is(assets.employeeWithheldPortion().doubleValue())))
        .andExpect(jsonPath("$.socialTaxPortion", is(assets.socialTaxPortion().doubleValue())))
        .andExpect(
            jsonPath(
                "$.additionalParentalBenefit",
                is(assets.additionalParentalBenefit().doubleValue())))
        .andExpect(jsonPath("$.interest", is(assets.interest().doubleValue())))
        .andExpect(jsonPath("$.compensation", is(assets.compensation().doubleValue())))
        .andExpect(jsonPath("$.insurance", is(assets.insurance().doubleValue())))
        .andExpect(jsonPath("$.corrections", is(assets.corrections().doubleValue())))
        .andExpect(jsonPath("$.inheritance", is(assets.inheritance().doubleValue())))
        .andExpect(jsonPath("$.withdrawals", is(assets.withdrawals().doubleValue())));
  }
}
