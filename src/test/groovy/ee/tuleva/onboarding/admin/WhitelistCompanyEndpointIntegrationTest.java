package ee.tuleva.onboarding.admin;

import static ee.tuleva.onboarding.event.TrackableEventType.SAVINGS_FUND_ONBOARDING_STATUS_CHANGE;
import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.REJECTED;
import static ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingStatus.WHITELISTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = "admin.api-token=it-token")
class WhitelistCompanyEndpointIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private SavingsFundOnboardingRepository repository;
  @Autowired private ApplicationEvents applicationEvents;

  @Test
  void whitelistCompany_persistsWhitelistedStatusAndEmitsAuditEvent() throws Exception {
    var registryCode = "16000000";
    assertThat(repository.findStatus(registryCode, LEGAL_ENTITY)).isEmpty();

    mockMvc
        .perform(
            post("/admin/whitelist-company")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("registryCode", registryCode))
        .andExpect(status().isOk());

    assertThat(repository.findStatus(registryCode, LEGAL_ENTITY)).contains(WHITELISTED);

    assertThat(applicationEvents.stream(TrackableSystemEvent.class))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getType()).isEqualTo(SAVINGS_FUND_ONBOARDING_STATUS_CHANGE);
              assertThat(event.getData())
                  .containsEntry("registryCode", registryCode)
                  .containsEntry("newStatus", WHITELISTED)
                  .containsEntry("outcome", "WHITELISTED")
                  .containsEntry("override", false);
            });
  }

  @Test
  void whitelistCompany_overridesExistingStatusWhenOverrideTrue() throws Exception {
    var registryCode = "16000001";
    repository.saveOnboardingStatus(registryCode, LEGAL_ENTITY, REJECTED);

    mockMvc
        .perform(
            post("/admin/whitelist-company")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("registryCode", registryCode)
                .param("override", "true"))
        .andExpect(status().isOk());

    assertThat(repository.findStatus(registryCode, LEGAL_ENTITY)).contains(WHITELISTED);
  }

  @Test
  void whitelistCompany_withExistingStatusAndNoOverride_returnsConflict() throws Exception {
    var registryCode = "16000002";
    repository.saveOnboardingStatus(registryCode, LEGAL_ENTITY, REJECTED);

    mockMvc
        .perform(
            post("/admin/whitelist-company")
                .with(csrf())
                .header("X-Admin-Token", "it-token")
                .param("registryCode", registryCode))
        .andExpect(status().isConflict());

    assertThat(repository.findStatus(registryCode, LEGAL_ENTITY)).contains(REJECTED);
  }
}
