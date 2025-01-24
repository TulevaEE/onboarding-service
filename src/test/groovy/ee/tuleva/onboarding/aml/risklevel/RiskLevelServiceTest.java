package ee.tuleva.onboarding.aml.risklevel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.aml.AmlCheck;
import ee.tuleva.onboarding.aml.AmlCheckRepository;
import ee.tuleva.onboarding.aml.AmlCheckType;
import ee.tuleva.onboarding.aml.notification.AmlCheckCreatedEvent;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.util.StringUtils;

@ExtendWith(MockitoExtension.class)
class RiskLevelServiceTest {

  @Mock JdbcClient jdbcClient;
  @Mock AmlCheckRepository amlCheckRepository;
  @Mock ApplicationEventPublisher eventPublisher;
  @InjectMocks RiskLevelService riskLevelService;
  @Captor ArgumentCaptor<AmlCheck> amlCheckCaptor;

  @Test
  @DisplayName(
      "We get 2 rows, one with a valid personal ID, one blank => we create exactly 1 new AML check")
  void testRunRiskLevelCheck_withRows() throws SQLException {
    StatementSpec statementSpecMock = mock(StatementSpec.class);
    when(jdbcClient.sql(contains("FROM analytics.v_aml_risk"))).thenReturn(statementSpecMock);
    when(statementSpecMock.query(any(RowMapper.class)))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              RowMapper<RiskLevelResult> rowMapper = invocation.getArgument(0);
              ResultSet rs1 = createMockResultSet("1234", 1, Map.of("atr1", 2));
              RiskLevelResult item1 = rowMapper.mapRow(rs1, 0);
              ResultSet rs2 = createMockResultSet("   ", 1, Map.of("atr1", 5));
              RiskLevelResult item2 = rowMapper.mapRow(rs2, 1);
              MappedQuerySpec<RiskLevelResult> mqs = mock(MappedQuerySpec.class);
              when(mqs.list()).thenReturn(List.of(item1, item2));
              return mqs;
            });
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            anyString(), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());
    riskLevelService.runRiskLevelCheck();
    verify(amlCheckRepository, times(1)).save(amlCheckCaptor.capture());
    AmlCheck createdCheck = amlCheckCaptor.getValue();
    assertEquals("1234", createdCheck.getPersonalCode());
    assertEquals(AmlCheckType.RISK_LEVEL, createdCheck.getType());
    assertFalse(createdCheck.isSuccess());
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
    verifyNoMoreInteractions(amlCheckRepository, eventPublisher);
  }

  @Test
  @DisplayName("The SQL query returns no rows => no loop => no new checks")
  void testRunRiskLevelCheck_noRows() {
    StatementSpec statementSpecMock = mock(StatementSpec.class);
    when(jdbcClient.sql(contains("FROM analytics.v_aml_risk"))).thenReturn(statementSpecMock);
    MappedQuerySpec<RiskLevelResult> querySpecMock = mock(MappedQuerySpec.class);
    when(statementSpecMock.query(any(RowMapper.class))).thenReturn(querySpecMock);
    when(querySpecMock.list()).thenReturn(Collections.emptyList());
    riskLevelService.runRiskLevelCheck();
    verify(amlCheckRepository, never()).save(any(AmlCheck.class));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName(
      "We directly test addCheckIfMissing() with no existing checks => a new check is created")
  void testAddCheckIfMissing_noExistingChecks() {
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("9999"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(Collections.emptyList());
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("9999")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("key", "val"))
            .build();
    boolean created = riskLevelService.addCheckIfMissing(newCheck);
    assertTrue(created);
    verify(amlCheckRepository).save(newCheck);
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
    verifyNoMoreInteractions(amlCheckRepository, eventPublisher);
  }

  @Test
  @DisplayName(
      "There's an existing check but with different metadata => a new check is still created")
  void testAddCheckIfMissing_existingCheckDifferentMetadata() {
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("1234")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("foo", "DIFFERENT"))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("1234"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("1234")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("foo", "bar"))
            .build();
    boolean created = riskLevelService.addCheckIfMissing(newCheck);
    assertTrue(created);
    verify(amlCheckRepository).save(newCheck);
    verify(eventPublisher).publishEvent(any(AmlCheckCreatedEvent.class));
    verifyNoMoreInteractions(amlCheckRepository, eventPublisher);
  }

  @Test
  @DisplayName("There's an existing check with identical metadata => skip")
  void testAddCheckIfMissing_existingCheckSameMetadata() {
    AmlCheck existingCheck =
        AmlCheck.builder()
            .personalCode("1234")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("foo", "bar"))
            .build();
    when(amlCheckRepository.findAllByPersonalCodeAndTypeAndSuccessIsFalseAndCreatedTimeAfter(
            eq("1234"), eq(AmlCheckType.RISK_LEVEL), any(Instant.class)))
        .thenReturn(List.of(existingCheck));
    AmlCheck newCheck =
        AmlCheck.builder()
            .personalCode("1234")
            .type(AmlCheckType.RISK_LEVEL)
            .success(false)
            .metadata(Map.of("foo", "bar"))
            .build();
    boolean created = riskLevelService.addCheckIfMissing(newCheck);
    assertFalse(created);
    verify(amlCheckRepository, never()).save(any(AmlCheck.class));
    verify(eventPublisher, never()).publishEvent(any());
    verifyNoMoreInteractions(amlCheckRepository, eventPublisher);
  }

  @Test
  @DisplayName("Check StringUtils.hasText() with blank and non-blank strings")
  void testStringUtilsHasText() {
    assertFalse(StringUtils.hasText("   "));
    assertTrue(StringUtils.hasText("abc"));
  }

  private ResultSet createMockResultSet(
      String personalId, int riskLevel, Map<String, Object> additionalCols) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    ResultSetMetaData metaData = mock(ResultSetMetaData.class);
    int totalCols = 2 + additionalCols.size();
    when(metaData.getColumnCount()).thenReturn(totalCols);
    when(metaData.getColumnLabel(1)).thenReturn("personal_id");
    when(metaData.getColumnLabel(2)).thenReturn("risk_level");
    int colIndex = 3;
    for (String extraKey : additionalCols.keySet()) {
      when(metaData.getColumnLabel(colIndex++)).thenReturn(extraKey);
    }
    when(rs.getMetaData()).thenReturn(metaData);
    when(rs.getString("personal_id")).thenReturn(personalId);
    when(rs.getInt("risk_level")).thenReturn(riskLevel);
    when(rs.getObject(2)).thenReturn(riskLevel);
    colIndex = 3;
    for (Map.Entry<String, Object> entry : additionalCols.entrySet()) {
      when(rs.getObject(colIndex++)).thenReturn(entry.getValue());
    }
    return rs;
  }
}
