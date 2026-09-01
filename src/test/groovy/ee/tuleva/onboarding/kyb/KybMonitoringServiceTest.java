package ee.tuleva.onboarding.kyb;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.kyb.survey.LatestKybSurveyInputs;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class KybMonitoringServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

  private final LegalEntityScreener legalEntityScreener = mock(LegalEntityScreener.class);
  private final OnboardedCompanies onboardedCompanies = mock(OnboardedCompanies.class);
  private final LatestKybSurveyInputs latestKybSurveyInputs = mock(LatestKybSurveyInputs.class);
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

  private final KybMonitoringService service =
      new KybMonitoringService(
          legalEntityScreener,
          onboardedCompanies,
          latestKybSurveyInputs,
          eventPublisher,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void screenAllCompaniesScreensEachCompany() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of("11111111", "22222222"));
    given(latestKybSurveyInputs.hasSurvey("11111111")).willReturn(true);
    given(latestKybSurveyInputs.hasSurvey("22222222")).willReturn(true);

    service.screenAllCompanies();

    verify(legalEntityScreener).screenLatest("11111111");
    verify(legalEntityScreener).screenLatest("22222222");
  }

  @Test
  void screenAllCompaniesSkipsCompaniesWithoutSurvey() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of("11111111", "22222222"));
    given(latestKybSurveyInputs.hasSurvey("11111111")).willReturn(true);
    given(latestKybSurveyInputs.hasSurvey("22222222")).willReturn(false);

    service.screenAllCompanies();

    verify(legalEntityScreener).screenLatest("11111111");
    verify(legalEntityScreener, never()).screenLatest("22222222");
  }

  @Test
  void screenAllCompaniesContinuesWhenOneCompanyFails() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of("11111111", "22222222"));
    given(latestKybSurveyInputs.hasSurvey("11111111")).willReturn(true);
    given(latestKybSurveyInputs.hasSurvey("22222222")).willReturn(true);
    doThrow(new IllegalStateException("boom")).when(legalEntityScreener).screenLatest("11111111");

    service.screenAllCompanies();

    verify(legalEntityScreener).screenLatest("11111111");
    verify(legalEntityScreener).screenLatest("22222222");
  }

  @Test
  void screenAllCompaniesHandlesEmptyCompanyList() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of());

    service.screenAllCompanies();

    verifyNoInteractions(legalEntityScreener);
  }

  @Test
  void screenAllCompaniesPublishesCompletionEventWithRunStartTime() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of("11111111"));
    given(latestKybSurveyInputs.hasSurvey("11111111")).willReturn(true);

    service.screenAllCompanies();

    verify(eventPublisher).publishEvent(new KybMonitoringCompletedEvent(NOW));
  }

  @Test
  void screenAllCompaniesPublishesCompletionEventEvenWhenScreeningsFail() {
    given(onboardedCompanies.registryCodes()).willReturn(List.of("11111111"));
    given(latestKybSurveyInputs.hasSurvey("11111111")).willReturn(true);
    doThrow(new IllegalStateException("boom")).when(legalEntityScreener).screenLatest("11111111");

    service.screenAllCompanies();

    verify(eventPublisher).publishEvent(new KybMonitoringCompletedEvent(NOW));
  }
}
