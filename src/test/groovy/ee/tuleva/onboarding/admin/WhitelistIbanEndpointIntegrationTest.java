package ee.tuleva.onboarding.admin;

import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.IbanWhitelistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "admin.api-token=it-token")
class WhitelistIbanEndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private IbanWhitelistService ibanWhitelistService;

  private static final PartyId PARTY = new PartyId(PERSON, "39901019992");
  private static final String IBAN = "EE471000001020145685";

  @Test
  void whitelistIban_persistsEntry() throws Exception {
    assertThat(ibanWhitelistService.isWhitelisted(PARTY, IBAN)).isFalse();

    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", IBAN)
                .param("comment", "verified via bank statement"))
        .andExpect(status().isOk());

    assertThat(ibanWhitelistService.isWhitelisted(PARTY, IBAN)).isTrue();
    assertThat(ibanWhitelistService.list(PARTY))
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.partyId()).isEqualTo(PARTY);
              assertThat(entry.iban()).isEqualTo(IBAN);
              assertThat(entry.comment()).isEqualTo("verified via bank statement");
            });
  }

  @Test
  void whitelistIban_canonicalizesIbanBeforeStoring() throws Exception {
    mockMvc
        .perform(
            post("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", "ee47 1000 0010 2014 5685"))
        .andExpect(status().isOk());

    assertThat(ibanWhitelistService.isWhitelisted(PARTY, IBAN)).isTrue();
    assertThat(ibanWhitelistService.list(PARTY))
        .singleElement()
        .satisfies(entry -> assertThat(entry.iban()).isEqualTo(IBAN));
  }

  @Test
  void removeIban_deletesEntry() throws Exception {
    ibanWhitelistService.add(PARTY, IBAN, "verified");
    assertThat(ibanWhitelistService.isWhitelisted(PARTY, IBAN)).isTrue();

    mockMvc
        .perform(
            delete("/admin/whitelist-iban")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("partyType", "PERSON")
                .param("partyCode", PARTY.code())
                .param("iban", IBAN))
        .andExpect(status().isOk());

    assertThat(ibanWhitelistService.isWhitelisted(PARTY, IBAN)).isFalse();
  }
}
