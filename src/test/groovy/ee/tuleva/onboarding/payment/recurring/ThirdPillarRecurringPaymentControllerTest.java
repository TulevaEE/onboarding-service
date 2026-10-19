package ee.tuleva.onboarding.payment.recurring;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.auth.role.RoleType.PERSON;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.role.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ThirdPillarRecurringPaymentController.class)
@AutoConfigureMockMvc
class ThirdPillarRecurringPaymentControllerTest {

  private static final String RECURRING_PAYMENT = "/v1/me/third-pillar/recurring-payment";

  @Autowired private MockMvc mvc;

  @MockitoBean private ThirdPillarStandingOrders standingOrders;

  @Test
  void detectsARecurringPaymentWhenThePersonPaysByStandingOrder() throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    given(standingOrders.paysByStandingOrder(person.getPersonalCode())).willReturn(true);

    mvc.perform(get(RECURRING_PAYMENT).with(authentication(authenticationOf(person))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            {"detected": true}
            """,
                    STRICT));
  }

  @Test
  void detectsNoRecurringPaymentWhenThePersonDoesNotPayByStandingOrder() throws Exception {
    var person = sampleAuthenticatedPersonNonMember().build();
    given(standingOrders.paysByStandingOrder(person.getPersonalCode())).willReturn(false);

    mvc.perform(get(RECURRING_PAYMENT).with(authentication(authenticationOf(person))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            {"detected": false}
            """,
                    STRICT));
  }

  @Test
  void looksAtThePersonTheUserActsFor() throws Exception {
    var person =
        sampleAuthenticatedPersonNonMember()
            .role(new Role(PERSON, "38501010002", "Represented Person"))
            .build();
    given(standingOrders.paysByStandingOrder("38501010002")).willReturn(true);

    mvc.perform(get(RECURRING_PAYMENT).with(authentication(authenticationOf(person))))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            {"detected": true}
            """,
                    STRICT));
  }

  private static Authentication authenticationOf(AuthenticatedPerson person) {
    return new UsernamePasswordAuthenticationToken(
        person, null, List.of(new SimpleGrantedAuthority(USER)));
  }
}
