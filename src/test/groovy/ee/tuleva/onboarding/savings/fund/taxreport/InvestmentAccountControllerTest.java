package ee.tuleva.onboarding.savings.fund.taxreport;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InvestmentAccountController.class)
@AutoConfigureMockMvc
class InvestmentAccountControllerTest {

  private static final String IBAN = "EE651010220306497226";

  @Autowired private MockMvc mvc;

  @MockitoBean private InvestmentAccountService investmentAccountService;

  private final AuthenticatedPerson authPerson = sampleAuthenticatedPersonNonMember().build();
  private final Authentication authentication =
      new UsernamePasswordAuthenticationToken(
          authPerson, null, List.of(new SimpleGrantedAuthority(USER)));

  @Test
  void answersWithNothingWhenNoAccountWasDeclared() throws Exception {
    given(investmentAccountService.declaredIban(eq(authPerson.getRoleCode())))
        .willReturn(Optional.empty());

    mvc.perform(get("/v1/savings-fund/investment-account").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.iban").doesNotExist());
  }

  @Test
  void answersWithTheDeclaredAccount() throws Exception {
    given(investmentAccountService.declaredIban(eq(authPerson.getRoleCode())))
        .willReturn(Optional.of(IBAN));

    mvc.perform(get("/v1/savings-fund/investment-account").with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.iban").value(IBAN));
  }

  @Test
  void keepsTheAccountThatWasDeclared() throws Exception {
    given(investmentAccountService.declare(eq(authPerson.getRoleCode()), eq(IBAN)))
        .willReturn(
            InvestmentAccount.builder().personalCode(authPerson.getRoleCode()).iban(IBAN).build());

    mvc.perform(
            put("/v1/savings-fund/investment-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"iban\":\"" + IBAN + "\"}")
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.iban").value(IBAN));
  }

  @Test
  void refusesAnAccountNumberThatIsNotOne() throws Exception {
    mvc.perform(
            put("/v1/savings-fund/investment-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"iban\":\"EE001\"}")
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(investmentAccountService);
  }

  @Test
  void refusesAnEmptyAccountNumber() throws Exception {
    mvc.perform(
            put("/v1/savings-fund/investment-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"iban\":\"\"}")
                .with(authentication(authentication))
                .with(csrf()))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(investmentAccountService);
  }
}
