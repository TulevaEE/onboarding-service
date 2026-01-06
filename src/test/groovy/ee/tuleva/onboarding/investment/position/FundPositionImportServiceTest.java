package ee.tuleva.onboarding.investment.position;

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
    when(repository.existsByNavDateAndFundCodeAndAssetName(any(), any(), any())).thenReturn(false);

    int imported = service.importPositions(List.of(position));

    assertThat(imported).isEqualTo(1);
    verify(repository).save(position);
  }

  @Test
  void importPositions_skipsExistingPositions() {
    FundPosition position = createPosition("TUK75", "Asset 1");
    when(repository.existsByNavDateAndFundCodeAndAssetName(
            position.getNavDate(), position.getFundCode(), position.getAssetName()))
        .thenReturn(true);

    int imported = service.importPositions(List.of(position));

    assertThat(imported).isEqualTo(0);
    verify(repository, never()).save(any());
  }

  @Test
  void importPositions_handlesMultiplePositions() {
    FundPosition existing = createPosition("TUK75", "Existing Asset");
    FundPosition newPosition = createPosition("TUK00", "New Asset");

    when(repository.existsByNavDateAndFundCodeAndAssetName(
            existing.getNavDate(), existing.getFundCode(), existing.getAssetName()))
        .thenReturn(true);
    when(repository.existsByNavDateAndFundCodeAndAssetName(
            newPosition.getNavDate(), newPosition.getFundCode(), newPosition.getAssetName()))
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

  private FundPosition createPosition(String fundCode, String assetName) {
    return FundPosition.builder()
        .reportDate(LocalDate.of(2026, 1, 6))
        .navDate(LocalDate.of(2026, 1, 5))
        .portfolio("Test Portfolio")
        .fundCode(fundCode)
        .assetType("Equities")
        .assetName(assetName)
        .quantity(new BigDecimal("1000"))
        .marketValue(new BigDecimal("10000"))
        .createdAt(Instant.now())
        .build();
  }
}
