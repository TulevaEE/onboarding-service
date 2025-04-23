package ee.tuleva.onboarding.analytics.transaction.fundbalance;

import ee.tuleva.onboarding.analytics.transaction.generic.AbstractTransactionSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.generic.SyncContext;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.TransactionFundBalanceDto;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class FundBalanceSynchronizer
    extends AbstractTransactionSynchronizer<TransactionFundBalanceDto, FundBalance> {

  private final FundBalanceRepository repository;

  public FundBalanceSynchronizer(EpisService episService, FundBalanceRepository repository) {
    super(episService);
    this.repository = repository;
  }

  @Getter
  @Builder
  private static class FundBalanceSyncContext implements SyncContext {
    private final LocalDate requestDate;
  }

  @Transactional
  public void sync(LocalDate requestDate) {
    FundBalanceSyncContext context =
        FundBalanceSyncContext.builder().requestDate(requestDate).build();
    super.syncInternal(context);
  }

  @Override
  protected List<TransactionFundBalanceDto> fetchTransactions(SyncContext context) {
    FundBalanceSyncContext ctx = (FundBalanceSyncContext) context;
    return episService.getFundBalances(ctx.getRequestDate());
  }

  @Override
  protected int deleteExistingTransactions(SyncContext context) {
    FundBalanceSyncContext ctx = (FundBalanceSyncContext) context;
    return repository.deleteByRequestDate(ctx.getRequestDate());
  }

  @Override
  protected FundBalance convertToEntity(TransactionFundBalanceDto dto, SyncContext context) {
    return FundBalance.builder()
        .securityName(dto.getSecurityName())
        .isin(dto.getIsin())
        .nav(dto.getNav())
        .balance(dto.getBalance())
        .countInvestors(dto.getCountInvestors())
        .countUnits(dto.getCountUnits())
        .countUnitsBron(dto.getCountUnitsBron())
        .countUnitsFree(dto.getCountUnitsFree())
        .countUnitsArest(dto.getCountUnitsArest())
        .countUnitsFm(dto.getCountUnitsFM())
        .fundManager(dto.getFundManager())
        .requestDate(dto.getRequestDate())
        .dateCreated(now())
        .build();
  }

  @Override
  protected void saveEntities(List<FundBalance> entities) {
    repository.saveAll(entities);
  }

  @Override
  protected String getTransactionTypeName() {
    return "fund balance";
  }

  @Override
  protected String getSyncIdentifier(SyncContext context) {
    FundBalanceSyncContext ctx = (FundBalanceSyncContext) context;
    return String.format("date=%s", ctx.getRequestDate());
  }
}
