package ee.tuleva.onboarding.party.admin;

import static ee.tuleva.onboarding.party.RepresentationType.GUARDIAN;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult;
import ee.tuleva.onboarding.party.ChildAmlBackfillService;
import ee.tuleva.onboarding.party.ChildIsNotAMinorException;
import ee.tuleva.onboarding.party.ChildSavingsOnboarding;
import ee.tuleva.onboarding.party.GuardianLinkRegistration;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.Created;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.EndDateChanged;
import ee.tuleva.onboarding.party.GuardianLinkRegistration.Unchanged;
import ee.tuleva.onboarding.party.ParentChildLink;
import ee.tuleva.onboarding.party.ParentChildLinkRegistrationService;
import ee.tuleva.onboarding.user.NameChangeBasis;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PartyAdminController.class)
@Import(AdminTokenValidator.class)
@TestPropertySource(properties = {"admin.api-token=valid-token", "admin.ops-token=ops-token"})
@WithMockUser
class PartyAdminControllerTest {

  private static final String VALID_LINK_BODY =
      """
      {
        "parentCode": "38812121215",
        "childCode": "61506150006",
        "childFirstName": "Mari",
        "childLastName": "Maasikas"
      }
      """;

  private static final String GUARDIAN_LINK_BODY =
      """
      {
        "guardianCode": "38812121215",
        "wardCode": "48806046007",
        "wardFirstName": "Ants",
        "wardLastName": "Haldja",
        "validUntil": "2099-12-31",
        "documentReference": "Court ruling 2-26-1234"
      }
      """;

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  @MockitoBean private ChildAmlBackfillService childAmlBackfillService;
  @MockitoBean private ChildSavingsOnboarding childSavingsOnboarding;
  @MockitoBean private Clock clock;

  @Test
  void childAmlBackfill_withOpsToken_delegatesToServiceAndReturnsTheReport() throws Exception {
    given(childAmlBackfillService.backfill("38812121215", true))
        .willReturn(ChildAmlBackfillResult.of(true, List.of()));

    mockMvc
        .perform(
            post("/admin/child-aml-backfill")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "dryRun": true}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dryRun").value(true))
        .andExpect(jsonPath("$.total").value(0));

    verify(childAmlBackfillService).backfill("38812121215", true);
  }

  @Test
  void childAmlBackfill_withInvalidToken_isUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/child-aml-backfill")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215", "dryRun": false}
                    """))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(childAmlBackfillService);
  }

  @Test
  void childAmlBackfill_withoutExplicitDryRun_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/child-aml-backfill")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "38812121215"}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(childAmlBackfillService);
  }

  @Test
  void childAmlBackfill_withInvalidRequesterCode_isBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/child-aml-backfill")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"requesterPersonalCode": "12345", "dryRun": false}
                    """))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(childAmlBackfillService);
  }

  @Test
  void createParentChildLink_withValidToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("61506150006")));

    verify(parentChildLinkRegistrationService)
        .register("38812121215", "61506150006", "Mari", "Maasikas", NameChangeBasis.admin(null));
    verify(childSavingsOnboarding).seedIfAbsent("61506150006");
  }

  @Test
  void createParentChildLink_withADocumentReference_namesItAsTheBasisOfTheChildsName()
      throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "parentCode": "38812121215",
                      "childCode": "61506150006",
                      "childFirstName": "Mari",
                      "childLastName": "Maasikas",
                      "documentReference": "Birth certificate copy, AML folder"
                    }
                    """))
        .andExpect(status().isOk());

    verify(parentChildLinkRegistrationService)
        .register(
            "38812121215",
            "61506150006",
            "Mari",
            "Maasikas",
            NameChangeBasis.admin("Birth certificate copy, AML folder"));
  }

  @Test
  void createParentChildLink_withOpsToken_delegatesToService() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isOk());

    verify(parentChildLinkRegistrationService)
        .register("38812121215", "61506150006", "Mari", "Maasikas", NameChangeBasis.admin(null));
    verify(childSavingsOnboarding).seedIfAbsent("61506150006");
  }

  private void givenCurrentDate(LocalDate date) {
    given(clock.instant()).willReturn(date.atStartOfDay(ZoneOffset.UTC).toInstant());
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
  }

  private static ParentChildLink.ParentChildLinkBuilder guardianLink() {
    return ParentChildLink.builder()
        .parentPersonalCode("38812121215")
        .childPersonalCode("48806046007")
        .relationshipType(GUARDIAN)
        .validUntil(LocalDate.of(2099, 12, 31));
  }

  private void givenGuardianRegistration(GuardianLinkRegistration registration) {
    given(
            parentChildLinkRegistrationService.registerGuardian(
                "38812121215",
                "48806046007",
                "Ants",
                "Haldja",
                LocalDate.of(2099, 12, 31),
                NameChangeBasis.admin("Court ruling 2-26-1234")))
        .willReturn(registration);
  }

  @Test
  void createGuardianLink_whenNoLinkExists_answersThatItWasCreated() throws Exception {
    givenCurrentDate(LocalDate.of(2026, 5, 22));
    givenGuardianRegistration(new Created(guardianLink().build()));

    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(GUARDIAN_LINK_BODY))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "Created guardian link: guardianCode=38812121215, wardCode=48806046007,"
                        + " validUntil=2099-12-31"));

    verify(parentChildLinkRegistrationService)
        .registerGuardian(
            "38812121215",
            "48806046007",
            "Ants",
            "Haldja",
            LocalDate.of(2099, 12, 31),
            NameChangeBasis.admin("Court ruling 2-26-1234"));
    verify(childSavingsOnboarding).seedIfAbsent("48806046007");
    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
  }

  @Test
  void createGuardianLink_onASuspendedLink_answersTheChangedEndDateAndThatItStaysSuspended()
      throws Exception {
    givenCurrentDate(LocalDate.of(2026, 5, 22));
    givenGuardianRegistration(
        new EndDateChanged(
            guardianLink().suspendedAt(Instant.parse("2026-03-01T10:00:00Z")).build(),
            LocalDate.of(2027, 1, 31)));

    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(GUARDIAN_LINK_BODY))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "Changed guardian link end date, link stays suspended:"
                        + " guardianCode=38812121215, wardCode=48806046007,"
                        + " previousValidUntil=2027-01-31, validUntil=2099-12-31"));
  }

  @Test
  void createGuardianLink_whenTheLinkAlreadyEndsOnThatDate_answersThatItIsUnchanged()
      throws Exception {
    givenCurrentDate(LocalDate.of(2026, 5, 22));
    givenGuardianRegistration(new Unchanged(guardianLink().build()));

    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(GUARDIAN_LINK_BODY))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "Guardian link already ends on that date, unchanged:"
                        + " guardianCode=38812121215, wardCode=48806046007,"
                        + " validUntil=2099-12-31"));
  }

  @Test
  void createGuardianLink_withoutValidUntil_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "guardianCode": "38812121215",
                      "wardCode": "48806046007",
                      "wardFirstName": "Ants",
                      "wardLastName": "Haldja"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(parentChildLinkRegistrationService, never())
        .registerGuardian(any(), any(), any(), any(), any(), any());
  }

  @Test
  void createGuardianLink_withValidUntilInThePast_returnsBadRequest() throws Exception {
    givenCurrentDate(LocalDate.of(2026, 5, 22));

    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "guardianCode": "38812121215",
                      "wardCode": "48806046007",
                      "wardFirstName": "Ants",
                      "wardLastName": "Haldja",
                      "validUntil": "2020-01-01"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(parentChildLinkRegistrationService, never())
        .registerGuardian(any(), any(), any(), any(), any(), any());
  }

  @Test
  void createGuardianLink_withValidUntilToday_returnsBadRequest() throws Exception {
    givenCurrentDate(LocalDate.of(2026, 5, 22));

    mockMvc
        .perform(
            post("/admin/guardian-link")
                .with(csrf())
                .header("X-Admin-Token", "ops-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "guardianCode": "38812121215",
                      "wardCode": "48806046007",
                      "wardFirstName": "Ants",
                      "wardLastName": "Haldja",
                      "validUntil": "2026-05-22"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(parentChildLinkRegistrationService, never())
        .registerGuardian(any(), any(), any(), any(), any(), any());
  }

  @Test
  void createParentChildLink_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isUnauthorized());

    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
    verify(childSavingsOnboarding, never()).seedIfAbsent(any());
  }

  @Test
  void createParentChildLink_whenChildNotAMinor_returnsBadRequest() throws Exception {
    doThrow(new ChildIsNotAMinorException("38812121215"))
        .when(parentChildLinkRegistrationService)
        .register(any(), any(), any(), any(), any());

    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(VALID_LINK_BODY))
        .andExpect(status().isBadRequest());

    verify(childSavingsOnboarding, never()).seedIfAbsent(any());
  }

  @Test
  void createParentChildLink_withInvalidPersonalCode_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/parent-child-link")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "parentCode": "not-a-code",
                      "childCode": "61506150006",
                      "childFirstName": "Mari",
                      "childLastName": "Maasikas"
                    }
                    """))
        .andExpect(status().isBadRequest());

    verify(parentChildLinkRegistrationService, never()).register(any(), any(), any(), any(), any());
  }
}
