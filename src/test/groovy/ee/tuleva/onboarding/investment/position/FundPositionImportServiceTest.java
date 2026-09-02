package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.investment.position.AccountType.SECURITY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionImportServiceTest {

  @Mock private FundPositionRepository repository;
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-02-06T12:00:00Z"), ZoneOffset.UTC);

  @InjectMocks private FundPositionImportService service;

  @Test
  void importNewPositions_savesNewPositions() {
    FundPosition position = createPosition(TUK75, "Asset 1");
    when(repository.existsByNavDateAndFundAndAccountTypeAndAccountName(any(), any(), any(), any()))
        .thenReturn(false);

    int imported = service.importNewPositions(List.of(position));

    assertThat(imported).isEqualTo(1);
    verify(repository).save(position);
  }

  @Test
  void importNewPositions_skipsExistingPositions() {
    FundPosition position = createPosition(TUK75, "Asset 1");
    when(repository.existsByNavDateAndFundAndAccountTypeAndAccountName(
            position.getNavDate(),
            position.getFund(),
            position.getAccountType(),
            position.getAccountName()))
        .thenReturn(true);

    int imported = service.importNewPositions(List.of(position));

    assertThat(imported).isEqualTo(0);
    verify(repository, never()).save(any());
  }

  @Test
  void importNewPositions_handlesMultiplePositions() {
    FundPosition existing = createPosition(TUK75, "Existing Asset");
    FundPosition newPosition = createPosition(TUK00, "New Asset");

    when(repository.existsByNavDateAndFundAndAccountTypeAndAccountName(
            existing.getNavDate(),
            existing.getFund(),
            existing.getAccountType(),
            existing.getAccountName()))
        .thenReturn(true);
    when(repository.existsByNavDateAndFundAndAccountTypeAndAccountName(
            newPosition.getNavDate(),
            newPosition.getFund(),
            newPosition.getAccountType(),
            newPosition.getAccountName()))
        .thenReturn(false);

    int imported = service.importNewPositions(List.of(existing, newPosition));

    assertThat(imported).isEqualTo(1);
    verify(repository).save(newPosition);
    verify(repository, never()).save(existing);
  }

  @Test
  void importNewPositions_returnsZero_whenEmptyList() {
    int imported = service.importNewPositions(List.of());

    assertThat(imported).isEqualTo(0);
    verify(repository, never()).save(any());
  }

  @Test
  void upsertPositions_updatesExistingWhenValuesChanged() {
    FundPosition incoming = createPosition(TUK75, "Asset 1");
    FundPosition existing =
        FundPosition.builder()
            .id(42L)
            .navDate(incoming.getNavDate())
            .fund(incoming.getFund())
            .accountType(incoming.getAccountType())
            .accountName(incoming.getAccountName())
            .accountId(incoming.getAccountId())
            .quantity(new BigDecimal("500"))
            .marketPrice(new BigDecimal("5"))
            .currency("EUR")
            .marketValue(new BigDecimal("2500"))
            .createdAt(Instant.parse("2026-01-05T10:00:00Z"))
            .build();

    when(repository.findByNavDateAndFundAndAccountTypeAndAccountName(
            incoming.getNavDate(),
            incoming.getFund(),
            incoming.getAccountType(),
            incoming.getAccountName()))
        .thenReturn(Optional.of(existing));

    var result = service.upsertPositions(List.of(incoming));

    assertThat(result.imported()).isEqualTo(0);
    assertThat(result.updated()).isEqualTo(1);
    assertThat(existing.getQuantity()).isEqualByComparingTo("1000");
    assertThat(existing.getMarketPrice()).isEqualByComparingTo("10");
    assertThat(existing.getMarketValue()).isEqualByComparingTo("10000");
    assertThat(existing.getUpdatedAt()).isNotNull();
    verify(repository).save(existing);
  }

  @Test
  void upsertPositions_reportsChangedRowsPerFund_countingUpdatedAndNewRows() {
    FundPosition changedTuk75 = createPosition(TUK75, "Asset 1");
    FundPosition unchangedTuk00 = createPosition(TUK00, "Asset 2");
    FundPosition newTuk75 = createPosition(TUK75, "Asset 3");
    FundPosition changedTuk75Again = createPosition(TUK75, "Asset 4");

    given(repository.findByNavDateAndFundAndAccountTypeAndAccountName(any(), any(), any(), any()))
        .willReturn(Optional.empty());
    given(
            repository.findByNavDateAndFundAndAccountTypeAndAccountName(
                changedTuk75.getNavDate(), TUK75, SECURITY, "Asset 1"))
        .willReturn(Optional.of(withQuantity(changedTuk75, "500")));
    given(
            repository.findByNavDateAndFundAndAccountTypeAndAccountName(
                changedTuk75Again.getNavDate(), TUK75, SECURITY, "Asset 4"))
        .willReturn(Optional.of(withQuantity(changedTuk75Again, "700")));
    given(
            repository.findByNavDateAndFundAndAccountTypeAndAccountName(
                unchangedTuk00.getNavDate(), TUK00, SECURITY, "Asset 2"))
        .willReturn(Optional.of(withQuantity(unchangedTuk00, "1000")));

    var result =
        service.upsertPositions(List.of(changedTuk75, unchangedTuk00, newTuk75, changedTuk75Again));

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(2);
    assertThat(result.changedRowsByFund()).isEqualTo(Map.of(TUK75, 3));
  }

  private static FundPosition withQuantity(FundPosition incoming, String quantity) {
    return FundPosition.builder()
        .id(42L)
        .navDate(incoming.getNavDate())
        .fund(incoming.getFund())
        .accountType(incoming.getAccountType())
        .accountName(incoming.getAccountName())
        .accountId(incoming.getAccountId())
        .quantity(new BigDecimal(quantity))
        .marketPrice(incoming.getMarketPrice())
        .currency("EUR")
        .marketValue(incoming.getMarketValue())
        .createdAt(Instant.parse("2026-01-05T10:00:00Z"))
        .build();
  }

  @Test
  void upsertPositions_skipsExistingWhenValuesSame() {
    FundPosition incoming = createPosition(TUK75, "Asset 1");
    FundPosition existing =
        FundPosition.builder()
            .id(42L)
            .navDate(incoming.getNavDate())
            .fund(incoming.getFund())
            .accountType(incoming.getAccountType())
            .accountName(incoming.getAccountName())
            .accountId(incoming.getAccountId())
            .quantity(new BigDecimal("1000"))
            .marketPrice(new BigDecimal("10"))
            .currency("EUR")
            .marketValue(new BigDecimal("10000"))
            .createdAt(Instant.parse("2026-01-05T10:00:00Z"))
            .build();

    when(repository.findByNavDateAndFundAndAccountTypeAndAccountName(
            incoming.getNavDate(),
            incoming.getFund(),
            incoming.getAccountType(),
            incoming.getAccountName()))
        .thenReturn(Optional.of(existing));

    var result = service.upsertPositions(List.of(incoming));

    assertThat(result.imported()).isEqualTo(0);
    assertThat(result.updated()).isEqualTo(0);
    assertThat(existing.getUpdatedAt()).isNull();
    verify(repository, never()).save(any());
  }

  @Test
  void upsertPositions_insertsNewPositions() {
    FundPosition incoming = createPosition(TUK75, "New Asset");
    when(repository.findByNavDateAndFundAndAccountTypeAndAccountName(
            incoming.getNavDate(),
            incoming.getFund(),
            incoming.getAccountType(),
            incoming.getAccountName()))
        .thenReturn(Optional.empty());

    var result = service.upsertPositions(List.of(incoming));

    assertThat(result.imported()).isEqualTo(1);
    assertThat(result.updated()).isEqualTo(0);
    verify(repository).save(incoming);
  }

  private FundPosition createPosition(TulevaFund fund, String accountName) {
    return FundPosition.builder()
        .navDate(LocalDate.of(2026, 1, 6))
        .fund(fund)
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
