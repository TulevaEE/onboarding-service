package ee.tuleva.onboarding.analytics.transaction.fund;

import ee.tuleva.onboarding.analytics.transaction.generic.AbstractTransactionSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.generic.SyncContext;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.FundTransactionDto;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FundTransactionSynchronizer
    extends AbstractTransactionSynchronizer<FundTransactionDto, FundTransaction> {

  private final FundTransactionRepository repository;

  public FundTransactionSynchronizer(
      EpisService episService, FundTransactionRepository repository) {
    super(episService);
    this.repository = repository;
  }

  @Getter
  @Builder
  private static class FundSyncContext implements SyncContext {
    private final String isin;
    private final LocalDate startDate;
    private final LocalDate endDate;
  }

  public void sync(String fundIsin, LocalDate startDate, LocalDate endDate) {
    FundSyncContext context =
        FundSyncContext.builder().isin(fundIsin).startDate(startDate).endDate(endDate).build();
    super.syncInternal(context);
  }

  @Override
  protected List<FundTransactionDto> fetchTransactions(SyncContext context) {
    FundSyncContext ctx = (FundSyncContext) context;
    return episService.getFundTransactions(ctx.getIsin(), ctx.getStartDate(), ctx.getEndDate());
  }

  @Override
  protected int deleteExistingTransactions(SyncContext context) {
    FundSyncContext ctx = (FundSyncContext) context;
    return repository.deleteByIsinAndTransactionDateBetween(
        ctx.getIsin(), ctx.getStartDate(), ctx.getEndDate());
  }

  @Override
  protected FundTransaction convertToEntity(FundTransactionDto dto, SyncContext context) {
    FundSyncContext ctx = (FundSyncContext) context;
    return FundTransaction.builder()
        .isin(ctx.getIsin())
        .transactionDate(dto.getDate())
        .personName(dto.getPersonName())
        .personalId(dto.getPersonId())
        .pensionAccount(dto.getPensionAccount())
        .country(dto.getCountry())
        .transactionType(dto.getTransactionType())
        .purpose(dto.getPurpose())
        .applicationType(dto.getApplicationType())
        .unitAmount(dto.getUnitAmount())
        .price(dto.getPrice())
        .nav(dto.getNav())
        .amount(dto.getAmount())
        .serviceFee(dto.getServiceFee())
        .dateCreated(now())
        .build();
  }

  @Override
  protected void saveEntities(List<FundTransaction> entities) {
    repository.saveAll(entities);
  }

  @Override
  protected String getTransactionTypeName() {
    return "fund";
  }

  @Override
  protected String getSyncIdentifier(SyncContext context) {
    FundSyncContext ctx = (FundSyncContext) context;
    return String.format(
        "ISIN=%s, range=%s to %s", ctx.getIsin(), ctx.getStartDate(), ctx.getEndDate());
  }
}
