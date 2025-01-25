package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
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
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RiskLevelServiceTest {

  private AmlRiskRepositoryService amlRiskRepositoryService;
  private AmlCheckRepository amlCheckRepository;
  private ApplicationEventPublisher eventPublisher;
  private RiskLevelService riskLevelService;

  @BeforeEach
  void setUp() {
    amlRiskRepositoryService = org.mockito.Mockito.mock(AmlRiskRepositoryService.class);
    amlCheckRepository = org.mockito.Mockito.mock(AmlCheckRepository.class);
    eventPublisher = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    riskLevelService =
        new RiskLevelService(amlRiskRepositoryService, amlCheckRepository, eventPublisher);
  }

  @Test
  @DisplayName("Should create a new AML check for a valid high-risk row with a personal ID")
  void testRunRiskLevelCheck_createsAmlCheck_forValidNewHighRiskRow() {
    RiskLevelResult row = new RiskLevelResult("38888888880", 1, Map.of("some_key", 999));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    riskLevelService.runRiskLevelCheck();

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(checkCaptor.capture());
    AmlCheck createdCheck = checkCaptor.getValue();
    assertEquals("38888888880", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    assertEquals(Map.of("some_key", 999), createdCheck.getMetadata());

    verify(eventPublisher)
        .publishEvent(org.mockito.ArgumentMatchers.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(e -> e instanceof AmlRiskLevelJobRunEvent)
            .map(e -> (AmlRiskLevelJobRunEvent) e)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip AML check creation when the personal ID is blank")
  void testRunRiskLevelCheck_skipsEmptyPersonalId() {
    RiskLevelResult row = new RiskLevelResult("   ", 1, Map.of());
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));

    riskLevelService.runRiskLevelCheck();

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never())
        .publishEvent(org.mockito.ArgumentMatchers.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(e -> e instanceof AmlRiskLevelJobRunEvent)
            .map(e -> (AmlRiskLevelJobRunEvent) e)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip creating a new AML check if duplicate exists within one year")
  void testRunRiskLevelCheck_skipsDuplicateWithinOneYear() {
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of(row));
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123"))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    riskLevelService.runRiskLevelCheck();

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never())
        .publishEvent(org.mockito.ArgumentMatchers.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(e -> e instanceof AmlRiskLevelJobRunEvent)
            .map(e -> (AmlRiskLevelJobRunEvent) e)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
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
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    riskLevelService.runRiskLevelCheck();

    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1))
        .publishEvent(org.mockito.ArgumentMatchers.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(e -> e instanceof AmlRiskLevelJobRunEvent)
            .map(e -> (AmlRiskLevelJobRunEvent) e)
            .findFirst()
            .orElseThrow();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should not create AML checks when no risk rows are returned")
  void testRunRiskLevelCheck_noRows_noAction() {
    when(amlRiskRepositoryService.getHighRiskRows()).thenReturn(List.of());

    riskLevelService.runRiskLevelCheck();

    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never())
        .publishEvent(org.mockito.ArgumentMatchers.isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent =
        jobEventCaptor.getAllValues().stream()
            .filter(e -> e instanceof AmlRiskLevelJobRunEvent)
            .map(e -> (AmlRiskLevelJobRunEvent) e)
            .findFirst()
            .orElseThrow();
    assertEquals(0, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }
}
