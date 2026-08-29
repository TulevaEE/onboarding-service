package ee.tuleva.onboarding.analytics.admin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.analytics.transaction.fundbalance.FundBalanceSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSnapshotDateValidator;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerSynchronizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class AnalyticsAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FundBalanceSynchronizer fundBalanceSynchronizer;
  @MockitoBean private UnitOwnerSynchronizer unitOwnerSynchronizer;
  @MockitoBean private UnitOwnerSnapshotDateValidator unitOwnerSnapshotDateValidator;
  @MockitoBean private Clock clock;

  @Test
  void backfillUnitCounts_callsSynchronizerWithDateRange() throws Exception {
    mockMvc
        .perform(
            post("/admin/backfill-unit-counts")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("from", "2026-03-01")
                .param("to", "2026-03-06"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-03-01")))
        .andExpect(content().string(containsString("2026-03-06")));

    verify(fundBalanceSynchronizer)
        .backfillUnitCounts(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 6));
  }

  @Test
  void syncUnitOwners_defaultsToTodaysDate() throws Exception {
    given(clock.instant()).willReturn(Instant.parse("2026-08-02T09:00:00Z"));
    given(clock.getZone()).willReturn(ZoneId.of("UTC"));

    mockMvc
        .perform(
            post("/admin/sync-unit-owners").with(csrf()).header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("2026-08-02")));

    verify(unitOwnerSnapshotDateValidator).validate(LocalDate.of(2026, 8, 2));
    verify(unitOwnerSynchronizer).sync(LocalDate.of(2026, 8, 2));
  }

  @Test
  void syncUnitOwners_validatesAnExplicitSnapshotDateBeforeSynchronizing() throws Exception {
    mockMvc
        .perform(
            post("/admin/sync-unit-owners")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("snapshotDate", "2026-08-01"))
        .andExpect(status().isOk());

    verify(unitOwnerSnapshotDateValidator).validate(LocalDate.of(2026, 8, 1));
    verify(unitOwnerSynchronizer).sync(LocalDate.of(2026, 8, 1));
  }

  @Test
  void syncUnitOwners_doesNotSynchronizeWhenTheDateIsRejected() throws Exception {
    doThrow(new IllegalArgumentException("too old"))
        .when(unitOwnerSnapshotDateValidator)
        .validate(LocalDate.of(2026, 1, 1));

    mockMvc
        .perform(
            post("/admin/sync-unit-owners")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .param("snapshotDate", "2026-01-01"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(unitOwnerSynchronizer);
  }

  @Test
  void syncUnitOwners_rejectsAnInvalidToken() throws Exception {
    mockMvc
        .perform(
            post("/admin/sync-unit-owners").with(csrf()).header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(unitOwnerSynchronizer);
  }
}
