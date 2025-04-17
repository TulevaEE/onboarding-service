package ee.tuleva.onboarding.analytics.transaction.exchange;

import ee.tuleva.onboarding.analytics.transaction.generic.AbstractTransactionSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.generic.SyncContext;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ExchangeTransactionDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ExchangeTransactionSynchronizer
    extends AbstractTransactionSynchronizer<ExchangeTransactionDto, ExchangeTransaction> {

  private final ExchangeTransactionRepository repository;

  public ExchangeTransactionSynchronizer(
      EpisService episService, ExchangeTransactionRepository repository) {
    super(episService);
    this.repository = repository;
  }

  @Getter
  @Builder
  private static class ExchangeSyncContext implements SyncContext {
    private final LocalDate startDate;
    private final Optional<String> securityFrom;
    private final Optional<String> securityTo;
    private final boolean pikFlag;
  }

  public void sync(
      LocalDate startDate,
      Optional<String> securityFrom,
      Optional<String> securityTo,
      boolean pikFlag) {
    ExchangeSyncContext context =
        ExchangeSyncContext.builder()
            .startDate(startDate)
            .securityFrom(securityFrom)
            .securityTo(securityTo)
            .pikFlag(pikFlag)
            .build();
    super.syncInternal(context);
  }

  @Override
  protected List<ExchangeTransactionDto> fetchTransactions(SyncContext context) {
    ExchangeSyncContext ctx = (ExchangeSyncContext) context;
    return episService.getExchangeTransactions(
        ctx.getStartDate(), ctx.getSecurityFrom(), ctx.getSecurityTo(), ctx.isPikFlag());
  }

  @Override
  protected int deleteExistingTransactions(SyncContext context) {
    ExchangeSyncContext ctx = (ExchangeSyncContext) context;
    return repository.deleteByReportingDate(ctx.getStartDate());
  }

  @Override
  protected ExchangeTransaction convertToEntity(ExchangeTransactionDto dto, SyncContext context) {
    ExchangeSyncContext ctx = (ExchangeSyncContext) context;
    return ExchangeTransaction.builder()
        .reportingDate(ctx.getStartDate())
        .securityFrom(dto.getSecurityFrom())
        .securityTo(dto.getSecurityTo())
        .fundManagerFrom(dto.getFundManagerFrom())
        .fundManagerTo(dto.getFundManagerTo())
        .code(dto.getCode())
        .firstName(dto.getFirstName())
        .name(dto.getName())
        .percentage(dto.getPercentage())
        .unitAmount(dto.getUnitAmount())
        .dateCreated(now())
        .build();
  }

  @Override
  protected void saveEntities(List<ExchangeTransaction> entities) {
    repository.saveAll(entities);
  }

  @Override
  protected String getTransactionTypeName() {
    return "exchange";
  }

  @Override
  protected String getSyncIdentifier(SyncContext context) {
    ExchangeSyncContext ctx = (ExchangeSyncContext) context;
    return String.format(
        "reportingDate=%s, securityFrom=%s, securityTo=%s, pikFlag=%s",
        ctx.getStartDate(),
        ctx.getSecurityFrom().orElse("N/A"),
        ctx.getSecurityTo().orElse("N/A"),
        ctx.isPikFlag());
  }
}
