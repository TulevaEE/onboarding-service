package ee.tuleva.onboarding.investment.transaction;

import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.simple.JdbcClient;

@ExtendWith(MockitoExtension.class)
class LedgerBalanceRepositoryTest {

  @Mock private JdbcClient jdbcClient;
  @Mock private FundValueRepository fundValueRepository;

  @Mock private JdbcClient.StatementSpec statementSpec;
  @Mock private JdbcClient.MappedQuerySpec<BigDecimal> querySpec;

  private LedgerBalanceRepository repository;

  @BeforeEach
  void setUp() {
    repository = new LedgerBalanceRepository(jdbcClient, fundValueRepository);
  }

  @Test
  void getIncomingPaymentsClearing_returnsBalance() {
    stubJdbcQuery(new BigDecimal("15000"));

    var result = repository.getIncomingPaymentsClearing();

    assertThat(result).isEqualByComparingTo(new BigDecimal("15000"));
  }

  @Test
  void getUnreconciledBankReceipts_returnsBalance() {
    stubJdbcQuery(new BigDecimal("8000"));

    var result = repository.getUnreconciledBankReceipts();

    assertThat(result).isEqualByComparingTo(new BigDecimal("8000"));
  }

  @Test
  void getFundUnitsReservedValue_withZeroUnits_returnsZero() {
    stubJdbcQuery(ZERO);

    var result = repository.getFundUnitsReservedValue();

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @Test
  void getFundUnitsReservedValue_withUnitsAndNav_returnsMultipliedValue() {
    stubJdbcQuery(new BigDecimal("100"));

    var nav = new FundValue("EE0000003283", null, new BigDecimal("1.50"), null, null);
    when(fundValueRepository.findLastValueForFund("EE0000003283")).thenReturn(Optional.of(nav));

    var result = repository.getFundUnitsReservedValue();

    assertThat(result).isEqualByComparingTo(new BigDecimal("150.00"));
  }

  @Test
  void getFundUnitsReservedValue_withUnitsAndNoNav_returnsZero() {
    stubJdbcQuery(new BigDecimal("100"));

    when(fundValueRepository.findLastValueForFund("EE0000003283")).thenReturn(Optional.empty());

    var result = repository.getFundUnitsReservedValue();

    assertThat(result).isEqualByComparingTo(ZERO);
  }

  @SuppressWarnings("unchecked")
  private void stubJdbcQuery(BigDecimal returnValue) {
    when(jdbcClient.sql(anyString())).thenReturn(statementSpec);
    when(statementSpec.param(anyString(), any())).thenReturn(statementSpec);
    when(statementSpec.query((Class<BigDecimal>) any(Class.class))).thenReturn(querySpec);
    when(querySpec.single()).thenReturn(returnValue);
  }
}
