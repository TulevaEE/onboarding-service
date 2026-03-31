package ee.tuleva.onboarding.aml.risklevel;

import static ee.tuleva.onboarding.aml.AmlCheckType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RiskLevelServiceTest {

  private AmlRiskReader amlRiskReader;
  private TkfRiskReader tkfRiskReader;
  private AmlCheckRepository amlCheckRepository;
  private ApplicationEventPublisher eventPublisher;
  private RiskLevelService riskLevelService;

  private static final double SOME_TEST_PROBABILITY = 0.00083333;

  @BeforeEach
  void setUp() {
    amlRiskReader = mock(AmlRiskReader.class);
    tkfRiskReader = mock(TkfRiskReader.class);
    amlCheckRepository = mock(AmlCheckRepository.class);
    eventPublisher = mock(ApplicationEventPublisher.class);
    ClockHolder.setClock(TestClockHolder.clock);
    riskLevelService =
        new RiskLevelService(amlRiskReader, tkfRiskReader, amlCheckRepository, eventPublisher);

    when(amlRiskReader.getHighRiskRows()).thenReturn(Collections.emptyList());
    when(amlRiskReader.getMediumRiskRowsSample(anyDouble())).thenReturn(Collections.emptyList());
    when(tkfRiskReader.getHighRiskRows()).thenReturn(Collections.emptyList());
    when(tkfRiskReader.getMediumRiskRowsSample(anyDouble())).thenReturn(Collections.emptyList());
  }

  @AfterEach
  void tearDown() {
    ClockHolder.setDefaultClock();
  }

  @Test
  @DisplayName("Should create a new AML check for a valid high-risk row with a personal ID")
  void testRunRiskLevelCheck_createsAmlCheck_forValidNewHighRiskRow() {
    // given
    RiskLevelResult highRiskRow = new RiskLevelResult("38888888880", 1, Map.of("some_key", 999));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(highRiskRow));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlRiskReader).refreshMaterializedView();
    verify(amlRiskReader).getHighRiskRows();
    verify(amlRiskReader).getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY));

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(checkCaptor.capture());
    AmlCheck createdCheck = checkCaptor.getValue();
    assertEquals("38888888880", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    assertEquals(999, createdCheck.getMetadata().get("some_key"));

    verify(eventPublisher).publishEvent(isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should create a new AML check for a valid medium-risk sample row")
  void testRunRiskLevelCheck_createsAmlCheck_forValidMediumRiskSample() {
    // given
    RiskLevelResult mediumRiskSample =
        new RiskLevelResult("49999999990", 2, Map.of("medium_key", 777));
    when(amlRiskReader.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(List.of(mediumRiskSample));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlRiskReader).getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY));

    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(checkCaptor.capture());
    AmlCheck createdCheck = checkCaptor.getValue();
    assertEquals("49999999990", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    assertEquals(777, createdCheck.getMetadata().get("medium_key"));

    verify(eventPublisher).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(0, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should process both high-risk and medium-risk sample rows")
  void testRunRiskLevelCheck_processesBothHighAndMediumRisk() {
    // given
    RiskLevelResult highRiskRow = new RiskLevelResult("38888888880", 1, Map.of("high_key", 999));
    RiskLevelResult mediumRiskSample =
        new RiskLevelResult("49999999990", 2, Map.of("medium_key", 777));

    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(highRiskRow));
    when(amlRiskReader.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(List.of(mediumRiskSample));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    ArgumentCaptor<AmlCheck> checkCaptor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository, times(2)).save(checkCaptor.capture());
    List<AmlCheck> createdChecks = checkCaptor.getAllValues();
    assertEquals(2, createdChecks.size());

    AmlCheck highRiskCheck =
        createdChecks.stream()
            .filter(c -> c.getPersonalCode().equals("38888888880"))
            .findFirst()
            .get();
    AmlCheck mediumRiskCheck =
        createdChecks.stream()
            .filter(c -> c.getPersonalCode().equals("49999999990"))
            .findFirst()
            .get();

    verify(eventPublisher, times(2)).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(1, jobRunEvent.getMediumRiskRowCount());
    assertEquals(2, jobRunEvent.getTotalRowsProcessed());
    assertEquals(2, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip AML check creation when the personal ID is blank")
  void testRunRiskLevelCheck_skipsEmptyPersonalId() {
    // given
    RiskLevelResult row = new RiskLevelResult("   ", 1, Map.of());
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should skip creating a new AML check if duplicate exists within six months")
  void testRunRiskLevelCheck_skipsDuplicateWithinSixMonths() {
    // given
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123", "level", 1))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName(
      "Should create a new AML check if the last same metadata check is older than six months")
  void testRunRiskLevelCheck_createsNewIfExistingIsOlderThanSixMonths() {
    // given
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should create a new AML check if existing check has different metadata")
  void testRunRiskLevelCheck_createsNewIfExistingIsDifferentMetadata() {
    // given
    RiskLevelResult row = new RiskLevelResult("38888888888", 1, Map.of("abc", "123"));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));

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

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should not create AML checks when no risk rows are returned")
  void testRunRiskLevelCheck_noRows_noAction() {
    // given
    when(amlRiskReader.getHighRiskRows()).thenReturn(Collections.emptyList());
    when(amlRiskReader.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(Collections.emptyList());

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(isA(AmlCheckCreatedEvent.class));

    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher, atLeastOnce()).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(0, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(0, jobRunEvent.getTotalRowsProcessed());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName(
      "addCheckIfMissing should return false when existing check has the same metadata instance")
  void addCheckIfMissing_returnsFalse_forSameMetadataInstance() {
    // given
    Map<String, Object> sharedMetadata = new HashMap<>();
    sharedMetadata.put("key", "value");
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(sharedMetadata)
            .type(AmlCheckType.RISK_LEVEL)
            .build();
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(sharedMetadata)
            .type(AmlCheckType.RISK_LEVEL)
            .build();

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            any(), any(), any()))
        .thenReturn(List.of(existingCheck));

    // when
    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    // then
    assertFalse(result);
    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  @DisplayName("addCheckIfMissing should return true when new check metadata is null")
  void addCheckIfMissing_returnsTrue_whenNewMetadataIsNull() {
    // given
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(null)
            .type(AmlCheckType.RISK_LEVEL)
            .build();
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(Map.of("key", "value"))
            .type(AmlCheckType.RISK_LEVEL)
            .build();

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            any(), any(), any()))
        .thenReturn(List.of(existingCheck));
    when(amlCheckRepository.save(any(AmlCheck.class))).thenReturn(newCheck);

    // when
    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    // then
    assertTrue(result);
    verify(amlCheckRepository).save(newCheck);
  }

  @Test
  @DisplayName("addCheckIfMissing should return true when existing check metadata is null")
  void addCheckIfMissing_returnsTrue_whenExistingMetadataIsNull() {
    // given
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(Map.of("key", "value"))
            .type(AmlCheckType.RISK_LEVEL)
            .build();
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("12345")
            .metadata(null)
            .type(AmlCheckType.RISK_LEVEL)
            .build();

    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            any(), any(), any()))
        .thenReturn(List.of(existingCheck));
    when(amlCheckRepository.save(any(AmlCheck.class))).thenReturn(newCheck);

    // when
    boolean result = riskLevelService.addCheckIfMissing(newCheck);

    // then
    assertTrue(result);
    verify(amlCheckRepository).save(newCheck);
  }

  @Test
  @DisplayName("Should skip duplicate check when metadata differs only in version field")
  void testRunRiskLevelCheck_skipsDuplicateWithDifferentVersion() {
    // given
    RiskLevelResult row =
        new RiskLevelResult("38888888888", 1, Map.of("abc", "123", "version", "2.0"));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123", "level", 1, "version", "1.0"))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(0, jobRunEvent.getAmlChecksCreatedCount());
  }

  @Test
  @DisplayName("Should create new check when metadata differs in fields other than version")
  void testRunRiskLevelCheck_createsNewCheckWhenMetadataDiffersExceptVersion() {
    // given
    RiskLevelResult row =
        new RiskLevelResult("38888888888", 1, Map.of("abc", "456", "version", "2.0"));
    when(amlRiskReader.getHighRiskRows()).thenReturn(List.of(row));

    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("38888888888")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("abc", "123", "level", 1, "version", "2.0"))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38888888888"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));

    // when
    riskLevelService.runRiskLevelCheck(SOME_TEST_PROBABILITY);

    // then
    verify(amlCheckRepository, times(1)).save(any(AmlCheck.class));
    verify(eventPublisher, times(1)).publishEvent(isA(AmlCheckCreatedEvent.class));
    ArgumentCaptor<AmlRiskLevelJobRunEvent> jobEventCaptor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(jobEventCaptor.capture());
    AmlRiskLevelJobRunEvent jobRunEvent = jobEventCaptor.getValue();
    assertEquals(1, jobRunEvent.getHighRiskRowCount());
    assertEquals(0, jobRunEvent.getMediumRiskRowCount());
    assertEquals(1, jobRunEvent.getTotalRowsProcessed());
    assertEquals(1, jobRunEvent.getAmlChecksCreatedCount());
  }

  // --- TKF risk level check tests ---

  @Test
  void tkfRiskLevelCheck_createsCheckForHighRiskRow() {
    RiskLevelResult tkfHighRisk =
        new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105));
    when(tkfRiskReader.getHighRiskRows()).thenReturn(List.of(tkfHighRisk));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38501010001"), eq(TKF_RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of());

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(tkfRiskReader).refreshMaterializedView();
    ArgumentCaptor<AmlCheck> captor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(captor.capture());

    AmlCheck tkfCheck = captor.getValue();
    assertThat(tkfCheck.getPersonalCode()).isEqualTo("38501010001");
    assertThat(tkfCheck.getType()).isEqualTo(TKF_RISK_LEVEL);
    assertThat(tkfCheck.isSuccess()).isFalse();
    assertThat(tkfCheck.getMetadata()).containsEntry("level", 1);
    assertThat(tkfCheck.getMetadata()).containsEntry("total_points", 105);
  }

  @Test
  void tkfRiskLevelCheck_skipsBlankPersonalId() {
    RiskLevelResult tkfBlank = new RiskLevelResult("   ", 1, Map.of("level", 1));
    when(tkfRiskReader.getHighRiskRows()).thenReturn(List.of(tkfBlank));

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  void tkfRiskLevelCheck_skipsDuplicateWithinSixMonths() {
    RiskLevelResult tkfRow =
        new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 105));
    when(tkfRiskReader.getHighRiskRows()).thenReturn(List.of(tkfRow));

    AmlCheck existing =
        AmlCheck.builder()
            .personalCode("38501010001")
            .type(TKF_RISK_LEVEL)
            .success(false)
            .metadata(Map.of("level", 1, "total_points", 105))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38501010001"), eq(TKF_RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existing));

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    verify(amlCheckRepository, never()).save(any());
  }

  @Test
  void tkfRiskLevelCheck_createsCheckWhenMetadataChanged() {
    RiskLevelResult tkfRow =
        new RiskLevelResult("38501010001", 1, Map.of("level", 1, "total_points", 110));
    when(tkfRiskReader.getHighRiskRows()).thenReturn(List.of(tkfRow));

    AmlCheck existing =
        AmlCheck.builder()
            .personalCode("38501010001")
            .type(TKF_RISK_LEVEL)
            .success(false)
            .metadata(Map.of("level", 1, "total_points", 105))
            .createdTime(TestClockHolder.now.minus(90, ChronoUnit.DAYS))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("38501010001"), eq(TKF_RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existing));
    when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    ArgumentCaptor<AmlCheck> captor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(captor.capture());
    assertThat(captor.getValue().getMetadata()).containsEntry("total_points", 110);
  }

  @Test
  void tkfRiskLevelCheck_processesMediumRiskSamples() {
    RiskLevelResult tkfMedium =
        new RiskLevelResult("39001010002", 2, Map.of("level", 2, "total_points", 20));
    when(tkfRiskReader.getMediumRiskRowsSample(eq(SOME_TEST_PROBABILITY)))
        .thenReturn(List.of(tkfMedium));
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("39001010002"), eq(TKF_RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of());
    when(amlCheckRepository.save(any(AmlCheck.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    ArgumentCaptor<AmlCheck> captor = ArgumentCaptor.forClass(AmlCheck.class);
    verify(amlCheckRepository).save(captor.capture());
    assertThat(captor.getValue().getType()).isEqualTo(TKF_RISK_LEVEL);
    assertThat(captor.getValue().getPersonalCode()).isEqualTo("39001010002");
  }

  @Test
  void tkfRiskLevelCheck_publishesEvent() {
    when(tkfRiskReader.getHighRiskRows()).thenReturn(List.of());
    when(tkfRiskReader.getMediumRiskRowsSample(anyDouble())).thenReturn(List.of());

    riskLevelService.runTkfRiskLevelCheck(SOME_TEST_PROBABILITY);

    ArgumentCaptor<AmlRiskLevelJobRunEvent> captor =
        ArgumentCaptor.forClass(AmlRiskLevelJobRunEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().getLabel()).isEqualTo("TKF");
  }
}
