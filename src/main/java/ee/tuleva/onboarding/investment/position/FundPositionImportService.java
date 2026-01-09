package ee.tuleva.onboarding.investment.position;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FundPositionImportService {

  private final FundPositionRepository repository;

  @Transactional
  public int importPositions(List<FundPosition> positions) {
    int imported = 0;

    for (FundPosition position : positions) {
      boolean exists =
          repository.existsByReportingDateAndFundCodeAndAccountName(
              position.getReportingDate(), position.getFundCode(), position.getAccountName());

      if (!exists) {
        repository.save(position);
        imported++;
      }
    }

    log.info(
        "Import completed: total={}, imported={}, skipped={}",
        positions.size(),
        imported,
        positions.size() - imported);

    return imported;
  }
}
