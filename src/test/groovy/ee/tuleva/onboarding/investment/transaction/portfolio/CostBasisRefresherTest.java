package ee.tuleva.onboarding.investment.transaction.portfolio;

import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.investment.transaction.PortfolioCostBasisService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class CostBasisRefresherTest {

  private static final Instant HALF_PAST_MIDNIGHT_IN_TALLINN_ON_20_NOV =
      Instant.parse("2026-11-19T22:30:00Z");
  private static final LocalDate TALLINN_TODAY = LocalDate.of(2026, 11, 20);
  private static final LocalDate SELF_HEAL_WINDOW_START = LocalDate.of(2026, 11, 6);

  @Mock private PortfolioCostBasisService service;
  @Mock private PortfolioBaselineRepository baselineRepository;
  @Mock private ObjectProvider<LockProvider> lockProviders;
  @Mock private LockProvider lockProvider;
  @Mock private SimpleLock selfHealLock;
  @Mock private SimpleLock dailyJobLock;

  private CostBasisRefresher refresher;

  @BeforeEach
  void setUp() {
    refresher =
        new CostBasisRefresher(
            Clock.fixed(HALF_PAST_MIDNIGHT_IN_TALLINN_ON_20_NOV, ZoneOffset.UTC),
            service,
            baselineRepository,
            lockProviders);
  }

  @Test
  void rebuildsTheFundsSelfHealWindowUpToTallinnTodayUnderBothCostBasisLocks() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.refreshed());
    InOrder inOrder = inOrder(lockProvider, service, selfHealLock, dailyJobLock);
    inOrder.verify(lockProvider).lock(lockNamed("PortfolioCostBasisSelfHealJob"));
    inOrder.verify(lockProvider).lock(lockNamed("PortfolioCostBasisJob"));
    inOrder.verify(service).rebuildRange(TUK75, SELF_HEAL_WINDOW_START, TALLINN_TODAY);
    inOrder.verify(selfHealLock).unlock();
    inOrder.verify(dailyJobLock).unlock();
  }

  @Test
  void leavesTheStoredLedgerWhenAnotherCostBasisJobHoldsALock() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.empty());

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.lockNotTaken());
    verify(selfHealLock).unlock();
    verifyNoInteractions(service);
  }

  @Test
  void reportsTheMissingLockProviderAsAFailureWithoutRebuildingUnlocked() {
    givenABaseline();
    given(lockProviders.getIfAvailable()).willReturn(null);

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh)
        .isEqualTo(
            CostBasisRefresh.failed(
                "Cost-basis refresh refused to run unlocked: no ShedLock LockProvider on this"
                    + " node"));
    verifyNoInteractions(service);
  }

  @Test
  void releasesTheOtherLockAndKeepsTheRefreshWhenOneReleaseFails() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));
    willThrow(new IllegalStateException("connection reset")).given(selfHealLock).unlock();

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.refreshed());
    verify(dailyJobLock).unlock();
  }

  @Test
  void alertsTheFirstNonBlankLineOfAMultiLineFailureWithoutItsFullStop() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));
    willThrow(
            new IllegalStateException(
                "\n  could not execute statement.\n[insert into investment_portfolio_cost_basis]"))
        .given(service)
        .rebuildRange(TUK75, SELF_HEAL_WINDOW_START, TALLINN_TODAY);

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.failed("could not execute statement"));
  }

  @Test
  void shortensAFailureLineTooLongForTheAlert() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));
    willThrow(new IllegalStateException("x".repeat(400)))
        .given(service)
        .rebuildRange(TUK75, SELF_HEAL_WINDOW_START, TALLINN_TODAY);

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.failed("x".repeat(300) + "…"));
  }

  @Test
  void reportsTheFailureAndReleasesTheLocksWhenTheRebuildThrows() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));
    willThrow(new IllegalStateException("Cost-basis oversell: fund=EE3600109435"))
        .given(service)
        .rebuildRange(TUK75, SELF_HEAL_WINDOW_START, TALLINN_TODAY);

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh)
        .isEqualTo(CostBasisRefresh.failed("Cost-basis oversell: fund=EE3600109435"));
    verify(selfHealLock).unlock();
    verify(dailyJobLock).unlock();
  }

  @Test
  void reportsTheFailureAndReleasesTheLockAlreadyTakenWhenTakingTheNextLockThrows() {
    givenABaseline();
    given(lockProviders.getIfAvailable()).willReturn(lockProvider);
    given(lockProvider.lock(lockNamed("PortfolioCostBasisSelfHealJob")))
        .willReturn(Optional.of(selfHealLock));
    given(lockProvider.lock(lockNamed("PortfolioCostBasisJob")))
        .willThrow(new IllegalStateException("shedlock table missing"));

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.failed("shedlock table missing"));
    verify(selfHealLock).unlock();
    verifyNoInteractions(service);
  }

  @Test
  void namesTheExceptionTypeWhenTheFailureHasNoMessage() {
    givenABaseline();
    givenTheLocks(Optional.of(selfHealLock), Optional.of(dailyJobLock));
    willThrow(new NullPointerException())
        .given(service)
        .rebuildRange(TUK75, SELF_HEAL_WINDOW_START, TALLINN_TODAY);

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.failed("NullPointerException"));
  }

  @Test
  void hasNothingToRefreshForAFundWithoutABaseline() {
    given(baselineRepository.findByFundIsin(TUK75.getIsin())).willReturn(Optional.empty());

    CostBasisRefresh refresh = refresher.refresh(TUK75);

    assertThat(refresh).isEqualTo(CostBasisRefresh.noBaseline());
    verifyNoInteractions(lockProviders, service);
  }

  private void givenABaseline() {
    given(baselineRepository.findByFundIsin(TUK75.getIsin()))
        .willReturn(Optional.of(PortfolioBaseline.builder().fundIsin(TUK75.getIsin()).build()));
  }

  private void givenTheLocks(Optional<SimpleLock> selfHeal, Optional<SimpleLock> dailyJob) {
    given(lockProviders.getIfAvailable()).willReturn(lockProvider);
    given(lockProvider.lock(lockNamed("PortfolioCostBasisSelfHealJob"))).willReturn(selfHeal);
    if (selfHeal.isPresent()) {
      given(lockProvider.lock(lockNamed("PortfolioCostBasisJob"))).willReturn(dailyJob);
    }
  }

  private static LockConfiguration lockNamed(String name) {
    return argThat(configuration -> configuration != null && configuration.getName().equals(name));
  }
}
