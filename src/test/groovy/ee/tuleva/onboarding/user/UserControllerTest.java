package ee.tuleva.onboarding.user;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUserNonMember;
import static ee.tuleva.onboarding.auth.authority.Authority.USER;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.NOT_CONTRIBUTING;
import static ee.tuleva.onboarding.user.SecondPillarContributionPeriod.Status.PAUSED;
import static ee.tuleva.onboarding.user.UserContactsFixture.contactSummaryWithContributionPeriods;
import static ee.tuleva.onboarding.user.UserContactsFixture.contributionPeriod;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import(SecondPillarContributions.class)
class UserControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private UserService userService;
  @MockitoBean private UserContacts userContacts;
  @MockitoBean private SecondPillarPaymentRateService secondPillarPaymentRateService;

  @TestConfiguration
  static class FixedClock {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), UTC);
    }
  }

  @Test
  void
      me_tellsALeaverWhoStoppedContributionsWhenTheyMayRejoinAlthoughTheRegistryStillShowsAFundChoice()
          throws Exception {
    var leaver =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"));

    me(leaver)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.secondPillarActive", is(true)))
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": false, "restartDate": null, "rejoinPossibleFrom": "2017-01-01"}}
                    """));
  }

  @Test
  void me_tellsALeaverWhoseContributionsEndedWhenTheyMayRejoin() throws Exception {
    var leaver = List.of(contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"));

    me(leaver)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": false, "restartDate": null, "rejoinPossibleFrom": "2017-01-01"}}
                    """));
  }

  @Test
  void me_tellsASaverWhoTookTheMoneyOutIn2021ThatTheyMayAlreadyRejoin() throws Exception {
    var withdrawer =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2021-08-31"),
            contributionPeriod(PAUSED, "2020-12-01", "2021-08-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2021-09-01"));

    me(withdrawer)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": false, "restartDate": null, "rejoinPossibleFrom": "2026-09-01"}}
                    """));
  }

  @Test
  void me_tellsARejoinerWhenTheProcessedRejoinRestartsContributions() throws Exception {
    var rejoiner =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01", "2011-12-31"),
            contributionPeriod(NOT_CONTRIBUTING, "2012-01-01"),
            contributionPeriod(CONTRIBUTING, "2027-05-01"));

    me(rejoiner)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": false, "restartDate": "2027-05-01", "rejoinPossibleFrom": null}}
                    """));
  }

  @Test
  void me_tellsASaverThatContributionsAreMade() throws Exception {
    var saver = List.of(contributionPeriod(CONTRIBUTING, "2002-07-01"));

    me(saver)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": true, "restartDate": null, "rejoinPossibleFrom": null}}
                    """));
  }

  @Test
  void me_tellsASaverInsideAPauseThatContributionsAreNotMadeWithoutARejoinDate() throws Exception {
    var pausedSaver =
        List.of(
            contributionPeriod(CONTRIBUTING, "2002-07-01"),
            contributionPeriod(PAUSED, "2026-09-01", "2027-08-31"));

    me(pausedSaver)
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
                    {"secondPillarContributions": {
                      "contributing": false, "restartDate": null, "rejoinPossibleFrom": null}}
                    """));
  }

  @Test
  void me_leavesContributionsOutWhenTheRegistryReportsNoContributionPeriods() throws Exception {
    me(List.of())
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    """
            {"secondPillarContributions": null}
            """));
  }

  private ResultActions me(List<SecondPillarContributionPeriod> contributionPeriods)
      throws Exception {
    var user = sampleUserNonMember().build();
    AuthenticatedPerson person = authenticatedPersonFromUser(user).build();
    given(userService.getById(user.getId())).willReturn(Optional.of(user));
    given(userContacts.forPerson(person))
        .willReturn(contactSummaryWithContributionPeriods(contributionPeriods));
    given(secondPillarPaymentRateService.getPaymentRates(person))
        .willReturn(new PaymentRates(2, null));

    return mvc.perform(
        get("/v1/me")
            .with(csrf())
            .with(
                authentication(
                    new UsernamePasswordAuthenticationToken(
                        person, null, List.of(new SimpleGrantedAuthority(USER))))));
  }
}
