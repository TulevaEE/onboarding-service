package ee.tuleva.onboarding.analytics.leavers;

import static ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransactionFixture.*;
import static ee.tuleva.onboarding.mandate.email.persistence.EmailType.SECOND_PILLAR_LEAVERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.analytics.transaction.exchange.ExchangeTransaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.MappedQuerySpec;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;

@ExtendWith(MockitoExtension.class)
class ExchangeTransactionLeaversRepositoryTest {

  @Mock JdbcClient jdbcClient;
  @InjectMocks ExchangeTransactionLeaversRepository repository;
  @Captor ArgumentCaptor<String> sqlCaptor;

  @Test
  @DisplayName("fetch returns list of leavers with expected mappings")
  void testFetch() {
    // GIVEN
    LocalDate startDate = LocalDate.of(2025, 1, 1);
    LocalDate endDate = LocalDate.of(2025, 1, 31);

    ExchangeTransaction testTransaction = exampleTransaction();
    ExchangeTransaction anotherTestTransaction = anotherExampleTransaction();

    List<ExchangeTransactionLeaver> expectedLeavers =
        List.of(createTestLeaver(testTransaction), createTestLeaver(anotherTestTransaction));

    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<ExchangeTransactionLeaver> mappedQuerySpec = mock(MappedQuerySpec.class);

    when(jdbcClient.sql(any())).thenReturn(statementSpec);
    when(statementSpec.param(eq("startDate"), eq(startDate))).thenReturn(statementSpec);
    when(statementSpec.param(eq("endDate"), eq(endDate))).thenReturn(statementSpec);
    when(statementSpec.query(ExchangeTransactionLeaver.class)).thenReturn(mappedQuerySpec);
    when(mappedQuerySpec.list()).thenReturn(expectedLeavers);

    // WHEN
    List<ExchangeTransactionLeaver> result = repository.fetch(startDate, endDate);

    // THEN
    assertEquals(expectedLeavers, result, "Should return the expected list of leavers");

    verify(jdbcClient).sql(sqlCaptor.capture());
    verify(statementSpec).param("startDate", startDate);
    verify(statementSpec).param("endDate", endDate);

    String sql = sqlCaptor.getValue();

    assertTrue(
        sql.contains("public.exchange_transaction"), "Should query exchange_transaction table");
    assertTrue(
        sql.contains("security_from AS \"currentFund\""),
        "Should map security_from to currentFund");
    assertTrue(sql.contains("security_to AS \"newFund\""), "Should map security_to to newFund");
    assertTrue(sql.contains("code AS \"personalCode\""), "Should map code to personalCode");
    assertTrue(sql.contains("first_name AS \"firstName\""), "Should map first_name to firstName");
    assertTrue(sql.contains("name AS \"lastName\""), "Should map name to lastName");
    assertTrue(
        sql.contains("unit_amount AS \"shareAmount\""), "Should map unit_amount to shareAmount");
    assertTrue(
        sql.contains("percentage AS \"sharePercentage\""),
        "Should map percentage to sharePercentage");
  }

  @Test
  @DisplayName("SQL query contains correct filtering criteria")
  void testSqlQueryFiltering() {
    // GIVEN
    LocalDate startDate = LocalDate.of(2025, 1, 1);
    LocalDate endDate = LocalDate.of(2025, 1, 31);

    StatementSpec statementSpec = mock(StatementSpec.class);
    MappedQuerySpec<ExchangeTransactionLeaver> mappedQuerySpec = mock(MappedQuerySpec.class);

    when(jdbcClient.sql(any())).thenReturn(statementSpec);
    when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
    when(statementSpec.query(ExchangeTransactionLeaver.class)).thenReturn(mappedQuerySpec);
    when(mappedQuerySpec.list()).thenReturn(List.of());

    // WHEN
    repository.fetch(startDate, endDate);

    // THEN
    verify(jdbcClient).sql(sqlCaptor.capture());
    String sql = sqlCaptor.getValue();

    assertTrue(sql.contains("date_created >= :startDate"), "Should filter by start date");
    assertTrue(sql.contains("date_created <= :endDate"), "Should filter by end date (inclusive)");
    assertTrue(sql.contains("security_from = 'EE3600109435'"), "Should filter by TUK75");
    assertTrue(sql.contains("security_from = 'EE3600109443'"), "Should filter by TUK00");
    assertTrue(sql.contains("security_to <> 'EE3600109443'"), "Should exclude TUK00 security_to");
    assertTrue(sql.contains("security_to <> 'EE3600109435'"), "Should exclude TUK75 security_to");
    assertTrue(sql.contains("ongoing_charges_figure >= 0.005"), "Should filter by charges");
    assertTrue(sql.contains("email IS NOT NULL"), "Should require email");
    assertTrue(sql.contains("keel = 'ENG'"), "Should filter by ENG language");
    assertTrue(sql.contains("keel = 'EST'"), "Should filter by EST language");
    assertTrue(sql.contains("percentage >= 10"), "Should filter by percentage");
  }

  @Test
  @DisplayName("getEmailType returns SECOND_PILLAR_LEAVERS")
  void testGetEmailType() {
    assertEquals(
        SECOND_PILLAR_LEAVERS,
        repository.getEmailType(),
        "Should return SECOND_PILLAR_LEAVERS email type");
  }

  private ExchangeTransactionLeaver createTestLeaver(ExchangeTransaction transaction) {
    return ExchangeTransactionLeaver.builder()
        .currentFund(transaction.getSecurityFrom())
        .newFund(transaction.getSecurityTo())
        .personalCode(transaction.getCode())
        .firstName(transaction.getFirstName())
        .lastName(transaction.getName())
        .shareAmount(transaction.getUnitAmount().doubleValue())
        .sharePercentage(transaction.getPercentage().doubleValue())
        .dateCreated(transaction.getReportingDate())
        .fundOngoingChargesFigure(0.01)
        .fundNameEstonian("Test Fund")
        .email("test@example.com")
        .language("EST")
        .lastEmailSentDate(LocalDateTime.now().minusDays(10))
        .build();
  }
}
