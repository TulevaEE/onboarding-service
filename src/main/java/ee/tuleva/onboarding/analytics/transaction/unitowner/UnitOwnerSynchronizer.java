package ee.tuleva.onboarding.analytics.transaction.unitowner;

import ee.tuleva.onboarding.analytics.transaction.generic.AbstractTransactionSynchronizer;
import ee.tuleva.onboarding.analytics.transaction.generic.SyncContext;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.transaction.Pillar2DetailsDto;
import ee.tuleva.onboarding.epis.transaction.Pillar3DetailsDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerBalanceDto;
import ee.tuleva.onboarding.epis.transaction.UnitOwnerDto;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UnitOwnerSynchronizer
    extends AbstractTransactionSynchronizer<UnitOwnerDto, UnitOwner> {

  private final UnitOwnerRepository repository;

  public UnitOwnerSynchronizer(EpisService episService, UnitOwnerRepository repository) {
    super(episService);
    this.repository = repository;
  }

  @Getter
  @Builder
  private static class UnitOwnerSyncContext implements SyncContext {
    private final LocalDate snapshotDate;
  }

  @Transactional
  public void sync(LocalDate snapshotDate) {
    log.info("Starting unit owner snapshot synchronization for date {}", snapshotDate);

    UnitOwnerSyncContext context =
        UnitOwnerSyncContext.builder().snapshotDate(snapshotDate).build();
    super.syncInternal(context);
  }

  @Override
  protected List<UnitOwnerDto> fetchTransactions(SyncContext context) {
    return episService.getUnitOwners();
  }

  @Override
  protected int deleteExistingTransactions(SyncContext context) {
    // Not deleting, since we sync snapshots
    return 0;
  }

  @Override
  protected UnitOwner convertToEntity(UnitOwnerDto dto, SyncContext context) {
    UnitOwnerSyncContext ctx = (UnitOwnerSyncContext) context;
    LocalDate snapshotDate = ctx.getSnapshotDate();

    UnitOwner.UnitOwnerBuilder builder =
        UnitOwner.builder()
            .personalId(dto.getPersonId())
            .snapshotDate(snapshotDate)
            .firstName(dto.getFirstName())
            .lastName(dto.getName())
            .phone(dto.getPhone())
            .email(dto.getEmail())
            .country(dto.getCountry())
            .languagePreference(dto.getLanguagePreference())
            .pensionAccount(dto.getPensionAccount())
            .deathDate(dto.getDeathDate())
            .fundManager(dto.getFundManager());

    Pillar2DetailsDto p2Dto = dto.getPillar2Details();
    if (p2Dto != null) {
      builder
          .p2choice(p2Dto.getChoice())
          .p2choiceMethod(p2Dto.getChoiceMethod())
          .p2choiceDate(p2Dto.getChoiceDate())
          .p2ravaDate(p2Dto.getRavaDate())
          .p2ravaStatus(p2Dto.getRavaStatus())
          .p2mmteDate(p2Dto.getMmteDate())
          .p2mmteStatus(p2Dto.getMmteStatus())
          .p2rate(p2Dto.getRate())
          .p2nextRate(p2Dto.getNextRate())
          .p2nextRateDate(p2Dto.getNextRateDate())
          .p2ykvaDate(p2Dto.getYkvaDate())
          .p2plavDate(p2Dto.getPlavDate())
          .p2fpaaDate(p2Dto.getFpaaDate())
          .p2dutyStart(p2Dto.getDutyStart())
          .p2dutyEnd(p2Dto.getDutyEnd());
    }

    Pillar3DetailsDto p3Dto = dto.getPillar3Details();
    if (p3Dto != null) {
      builder
          .p3identificationDate(p3Dto.getIdentificationDate())
          .p3identifier(p3Dto.getIdentifier())
          .p3blockFlag(p3Dto.getBlockFlag())
          .p3blocker(p3Dto.getBlocker());
    }

    List<UnitOwnerBalanceDto> balanceDtos = dto.getBalances();
    if (balanceDtos != null && !balanceDtos.isEmpty()) {
      builder.balances(
          balanceDtos.stream()
              .map(
                  bDto ->
                      UnitOwnerBalanceEmbeddable.builder()
                          .securityShortName(bDto.getSecurityShortName())
                          .securityName(bDto.getSecurityName())
                          .type(bDto.getType())
                          .amount(bDto.getAmount())
                          .startDate(bDto.getStartDate())
                          .lastUpdated(bDto.getLastUpdated())
                          .build())
              .collect(Collectors.toList()));
    } else {
      builder.balances(Collections.emptyList());
    }

    LocalDateTime now = now();
    builder.dateCreated(now);

    return builder.build();
  }

  @Override
  protected void saveEntities(List<UnitOwner> entities) {
    repository.saveAll(entities);
  }

  @Override
  protected String getTransactionTypeName() {
    return "unit owner snapshot";
  }

  @Override
  protected String getSyncIdentifier(SyncContext context) {
    UnitOwnerSyncContext ctx = (UnitOwnerSyncContext) context;
    return String.format("SnapshotDate=%s", ctx.getSnapshotDate());
  }
}
