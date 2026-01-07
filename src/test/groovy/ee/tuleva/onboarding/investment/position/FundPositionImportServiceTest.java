package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionImportServiceTest {

  @Mock private FundPositionRepository repository;

  @InjectMocks private FundPositionImportService service;

  @Test
  void importPositions_savesNewPositions() {
    FundPosition position = createPosition("TUK75", "Asset 1");
    when(repository.existsByReportingDateAndFundCodeAndAccountName(any(), any(), any()))
        .thenReturn(false);

    int imported = service.importPositions(List.of(position));

    assertThat(imported).isEqualTo(1);
    verify(repository).save(position);
  }

  @Test
  void importPositions_skipsExistingPositions() {
    FundPosition position = createPosition("TUK75", "Asset 1");
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            position.getReportingDate(), position.getFundCode(), position.getAccountName()))
        .thenReturn(true);

    int imported = service.importPositions(List.of(position));

    assertThat(imported).isEqualTo(0);
    verify(repository, never()).save(any());
  }

  @Test
  void importPositions_handlesMultiplePositions() {
    FundPosition existing = createPosition("TUK75", "Existing Asset");
    FundPosition newPosition = createPosition("TUK00", "New Asset");

    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            existing.getReportingDate(), existing.getFundCode(), existing.getAccountName()))
        .thenReturn(true);
    when(repository.existsByReportingDateAndFundCodeAndAccountName(
            newPosition.getReportingDate(),
            newPosition.getFundCode(),
            newPosition.getAccountName()))
        .thenReturn(false);

    int imported = service.importPositions(List.of(existing, newPosition));

    assertThat(imported).isEqualTo(1);
    verify(repository).save(newPosition);
    verify(repository, never()).save(existing);
  }

  @Test
  void importPositions_returnsZero_whenEmptyList() {
    int imported = service.importPositions(List.of());

    assertThat(imported).isEqualTo(0);
    verify(repository, never()).save(any());
  }

  private FundPosition createPosition(String fundCode, String accountName) {
    return FundPosition.builder()
        .reportingDate(LocalDate.of(2026, 1, 6))
        .fundCode(fundCode)
        .accountType(SECURITY)
        .accountName(accountName)
        .accountId("IE00BFG1TM61")
        .quantity(new BigDecimal("1000"))
        .marketPrice(new BigDecimal("10"))
        .currency("EUR")
        .marketValue(new BigDecimal("10000"))
        .createdAt(Instant.now())
        .build();
  }
}
