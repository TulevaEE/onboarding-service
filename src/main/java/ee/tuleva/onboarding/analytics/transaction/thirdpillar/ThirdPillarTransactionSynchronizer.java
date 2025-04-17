package ee.tuleva.onboarding.analytics.transaction.thirdpillar;

import ee.tuleva.onboarding.analytics.transaction.generic.AbstractTransactionSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.generic.SyncContext;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.ThirdPillarTransactionDto;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ThirdPillarTransactionSynchronizer
    extends AbstractTransactionSynchronizer<
        ThirdPillarTransactionDto, AnalyticsThirdPillarTransaction> {

  private final AnalyticsThirdPillarTransactionRepository repository;

  public ThirdPillarTransactionSynchronizer(
      EpisService episService, AnalyticsThirdPillarTransactionRepository repository) {
    super(episService);
    this.repository = repository;
  }

  @Getter
  @Builder
  private static class ThirdPillarSyncContext implements SyncContext {
    private final LocalDate startDate;
    private final LocalDate endDate;
  }

  public void sync(LocalDate startDate, LocalDate endDate) {
    ThirdPillarSyncContext context =
        ThirdPillarSyncContext.builder().startDate(startDate).endDate(endDate).build();
    super.syncInternal(context);
  }

  @Override
  protected List<ThirdPillarTransactionDto> fetchTransactions(SyncContext context) {
    ThirdPillarSyncContext ctx = (ThirdPillarSyncContext) context;
    return episService.getTransactions(ctx.getStartDate(), ctx.getEndDate());
  }

  @Override
  protected int deleteExistingTransactions(SyncContext context) {
    ThirdPillarSyncContext ctx = (ThirdPillarSyncContext) context;
    return repository.deleteByReportingDateBetween(ctx.getStartDate(), ctx.getEndDate());
  }

  @Override
  protected AnalyticsThirdPillarTransaction convertToEntity(
      ThirdPillarTransactionDto dto, SyncContext context) {
    return AnalyticsThirdPillarTransaction.builder()
        .reportingDate(dto.getDate())
        .fullName(dto.getPersonName())
        .personalId(dto.getPersonId())
        .accountNo(dto.getPensionAccount())
        .country(dto.getCountry())
        .transactionType(dto.getTransactionType())
        .transactionSource(dto.getPurpose())
        .applicationType(dto.getApplicationType())
        .shareAmount(dto.getUnitAmount())
        .sharePrice(dto.getPrice())
        .nav(dto.getNav())
        .transactionValue(dto.getAmount())
        .serviceFee(dto.getServiceFee())
        .fundManager(dto.getFundManager())
        .fund(dto.getFund())
        .purposeCode(dto.getPurposeCode())
        .counterpartyName(dto.getCounterpartyName())
        .counterpartyCode(dto.getCounterpartyCode())
        .counterpartyBankAccount(dto.getCounterpartyBankAccount())
        .counterpartyBank(dto.getCounterpartyBank())
        .counterpartyBic(dto.getCounterpartyBic())
        .dateCreated(now())
        .build();
  }

  @Override
  protected void saveEntities(List<AnalyticsThirdPillarTransaction> entities) {
    repository.saveAll(entities);
  }

  @Override
  protected String getTransactionTypeName() {
    return "third pillar";
  }

  @Override
  protected String getSyncIdentifier(SyncContext context) {
    ThirdPillarSyncContext ctx = (ThirdPillarSyncContext) context;
    return String.format("range=%s to %s", ctx.getStartDate(), ctx.getEndDate());
  }
}
