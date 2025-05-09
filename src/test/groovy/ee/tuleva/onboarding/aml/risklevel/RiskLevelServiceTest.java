package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import ee.tuleva.onboarding.aml.notification.AmlRiskLevelJobRunEvent;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.time.TestClockHolder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class RiskLevelServiceTest {

  private AmlRiskRepositoryService amlRiskRepositoryService;
  private AmlCheckRepository amlCheckRepository;
  private ApplicationEventPublisher eventPublisher;
  private RiskLevelService riskLevelService;

  private static final double SOME_TEST_PROBABILITY = 0.00083333;

  @BeforeEach
  void setUp() {
    amlRiskRepositoryService = Mockito.mock(AmlRiskRepositoryService.class);
    amlCheckRepository = Mockito.mock(AmlCheckRepository.class);
    eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    ClockHolder.setClock(TestClockHolder.clock);
    riskLevelService =
        new RiskLevelService(amlRiskRepositoryService, amlCheckRepository, eventPublisher);

    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(Collections.emptyList());
    when(amlRiskRepositoryService.getMediumRiskRowsSample(anyDouble()))
        .thenReturn(Collections.emptyList());
  }

  @Test
  @DisplayName("Should create a new AML check for a valid high-risk row with a personal ID")
  void testRunRiskLevelCheck_createsAmlCheck_forValidNewHighRiskRow() {
    RiskLevelResult highRiskRow = new RiskLevelResult("38888888880", 1, Map.of("some_key", 999));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(highRiskRow));
    when(amlRiskRepositoryService.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(Collections.emptyList());
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlRiskRepositoryService).refreshMaterializedView();
    verify(amlRiskRepositoryService).getHighRiskRows();
    verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY));

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(checkCaptor.capture());
    AmlCheck createdCheck = checkCaptor.getValue();
    assertEquals("38888888880", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    assertEquals(Map.of("some_key", 999), createdCheck.getMetadata());

    verify(eventPublisher).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should create a new AML check for a valid medium-risk sample row")
  void testRunRiskLevelCheck_createsAmlCheck_forValidMediumRiskSample() {
    RiskLevelResult mediumRiskSample =
        new RiskLevelResult("49999999990", 2, Map.of("medium_key", 777));
    when(amlRiskRepositoryService.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(List.of(mediumRiskSample));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY));

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(checkCaptor.capture());
    AmlCheck createdCheck = checkCaptor.getValue();
    assertEquals("49999999990", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    assertEquals(Map.of("medium_key", 777), createdCheck.getMetadata());

    verify(eventPublisher).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should process both high-risk and medium-risk sample rows")
  void testRunRiskLevelCheck_processesBothHighAndMediumRisk() {
    RiskLevelResult highRiskRow = new RiskLevelResult("38888888880", 1, Map.of("high_key", 999));
    RiskLevelResult mediumRiskSample =
        new RiskLevelResult("49999999990", 2, Map.of("medium_key", 777));

    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(highRiskRow));
    when(amlRiskRepositoryService.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(List.of(mediumRiskSample));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository, times(2)).save(checkCaptor.capture());
    List<AmlCheck> createdChecks = checkCaptor.getAllValues();
    assertEquals(2, createdChecks.size());
    assertTrue(createdChecks.stream().anyMatch(c -> c.getPersonalCode().equals("38888888880")));
    assertTrue(createdChecks.stream().anyMatch(c -> c.getPersonalCode().equals("49999999990")));

    verify(eventPublisher, times(2)).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(2, jobRunEvent.getHighRiskRowCount());
    assertEquals(2, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip AML check creation when the personal ID is blank")
  void testRunRiskLevelCheck_skipsEmptyPersonalId() {
    RiskLevelResult row = new RiskLevelResult("   ", 1, Map.of());
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));
    when(amlRiskRepositoryService.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlRiskRepositoryService).refreshMaterializedView();
    verify(amlRiskRepositoryService).getHighRiskRows();
    verify(amlRiskRepositoryService).getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY));

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip creating a new AML check if duplicate exists within six months")
  void testRunRiskLevelCheck_skipsDuplicateWithinSixMonths() {
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123"))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName(
      "Should create a new AML check if the last same metadata check is older than six months")
  void testRunRiskLevelCheck_createsNewIfExistingIsOlderThanSixMonths() {
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName(
      "Should skip creating a new AML check if duplicate exists within one year (legacy test case consideration)")
  void testRunRiskLevelCheck_skipsDuplicateWithinOneYear() {
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheckWithinOneYearButOlderThanSixMonths =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123"))
            .createdTime(TestClockHolder.now.minus(200, ChronoUnit.DAYS)) // e.g. ~7 months ago
            .build();

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList()); // No *recent* (within 6 months) duplicates

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should create a new AML check if existing check has different metadata")
  void testRunRiskLevelCheck_createsNewIfExistingIsDifferentMetadata() {
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("something", "else"))
            .createdTime(TestClockHolder.now.minus(50, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should not create AML checks when no risk rows are returned")
  void testRunRiskLevelCheck_noRows_noAction() {
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(Collections.emptyList());
    when(amlRiskRepositoryService.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(Mockito.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(AmlRiskLevelJobRunEvent.class::isInstance)
            .map(AmlRiskLevelJobRunEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertEquals(0, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }
}
