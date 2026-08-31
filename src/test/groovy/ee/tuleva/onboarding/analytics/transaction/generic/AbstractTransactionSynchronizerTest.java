package ee.tuleva.onboarding.analytics.transaction.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.time.FixedClockConfig;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AbstractTransactionSynchronizerTest extends FixedClockConfig {

  @Mock private EpisService episService;
  @Mock private PlatformTransactionManager transactionManager;

  private final SyncContext context = new SyncContext() {};

  private TransactionTemplate transactionTemplate;
  private RecordingTransactionSynchronizer synchronizer;
  private Logger logger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManager);
    synchronizer = new RecordingTransactionSynchronizer(episService, transactionTemplate);
    logger = (Logger) LoggerFactory.getLogger(AbstractTransactionSynchronizer.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
  }

  @Test
  void emptyFetchResultSkipsDeleteAndSaveAndOpensNoTransaction() {
    synchronizer.transactionsToFetch = List.of();

    SyncResult result = synchronizer.triggerSync(context);

    assertThat(synchronizer.callLog)
        .containsExactly("getTransactionTypeName", "getSyncIdentifier", "fetch");
    assertThat(synchronizer.savedEntities).isEmpty();
    then(transactionManager).shouldHaveNoInteractions();
    assertThat(result).isEqualTo(new SyncResult("test-type", "test-id", 0, 0));
  }

  @Test
  void nonEmptyFetchResultConvertsThenDeletesThenSavesInsideOneTransaction() {
    SimpleTransactionStatus status = new SimpleTransactionStatus();
    given(transactionManager.getTransaction(any())).willReturn(status);
    synchronizer.transactionsToFetch = List.of("a", "b");
    synchronizer.deleteResult = 3;

    SyncResult result = synchronizer.triggerSync(context);

    assertThat(synchronizer.callLog)
        .containsExactly(
            "getTransactionTypeName",
            "getSyncIdentifier",
            "fetch",
            "convert:a",
            "convert:b",
            "delete",
            "save:2");
    assertThat(synchronizer.savedEntities)
        .containsExactly(new TestEntity("a"), new TestEntity("b"));
    assertThat(synchronizer.receivedContexts).containsOnly(context);
    then(transactionManager).should().commit(status);
    then(transactionManager).should(never()).rollback(any());
    assertThat(result).isEqualTo(new SyncResult("test-type", "test-id", 3, 2));
  }

  @Test
  void fetchFailureIsLoggedAndSwallowedWithoutDeleteConvertOrSave() {
    synchronizer.fetchFailure = new RuntimeException("EPIS unavailable");

    AtomicReference<SyncResult> result = new AtomicReference<>();
    assertThatCode(() -> result.set(synchronizer.triggerSync(context))).doesNotThrowAnyException();

    assertThat(synchronizer.callLog)
        .containsExactly("getTransactionTypeName", "getSyncIdentifier", "fetch");
    assertThat(synchronizer.savedEntities).isEmpty();
    then(transactionManager).shouldHaveNoInteractions();
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).hasSize(1);
    assertThat(result.get()).isEqualTo(new SyncResult("test-type", "test-id", 0, 0));
  }

  @Test
  void convertFailureIsLoggedAndSwallowedWithoutOpeningTransaction() {
    synchronizer.transactionsToFetch = List.of("a");
    synchronizer.convertFailure = new RuntimeException("bad dto");

    AtomicReference<SyncResult> result = new AtomicReference<>();
    assertThatCode(() -> result.set(synchronizer.triggerSync(context))).doesNotThrowAnyException();

    assertThat(synchronizer.callLog)
        .containsExactly("getTransactionTypeName", "getSyncIdentifier", "fetch", "convert:a");
    then(transactionManager).shouldHaveNoInteractions();
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).hasSize(1);
    assertThat(result.get()).isEqualTo(new SyncResult("test-type", "test-id", 0, 0));
  }

  @Test
  void deleteFailureRollsBackTransactionAndSkipsSaveButIsSwallowed() {
    SimpleTransactionStatus status = new SimpleTransactionStatus();
    given(transactionManager.getTransaction(any())).willReturn(status);
    synchronizer.transactionsToFetch = List.of("a");
    synchronizer.deleteFailure = new RuntimeException("db locked");

    AtomicReference<SyncResult> result = new AtomicReference<>();
    assertThatCode(() -> result.set(synchronizer.triggerSync(context))).doesNotThrowAnyException();

    assertThat(synchronizer.callLog)
        .containsExactly(
            "getTransactionTypeName", "getSyncIdentifier", "fetch", "convert:a", "delete");
    assertThat(synchronizer.savedEntities).isEmpty();
    then(transactionManager).should().rollback(status);
    then(transactionManager).should(never()).commit(any());
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).hasSize(1);
    assertThat(result.get()).isEqualTo(new SyncResult("test-type", "test-id", 0, 0));
  }

  @Test
  void saveFailureRollsBackTransactionAfterSaveWasAttemptedButIsSwallowed() {
    SimpleTransactionStatus status = new SimpleTransactionStatus();
    given(transactionManager.getTransaction(any())).willReturn(status);
    synchronizer.transactionsToFetch = List.of("a");
    synchronizer.deleteResult = 1;
    synchronizer.saveFailure = new RuntimeException("constraint violation");

    AtomicReference<SyncResult> result = new AtomicReference<>();
    assertThatCode(() -> result.set(synchronizer.triggerSync(context))).doesNotThrowAnyException();

    assertThat(synchronizer.callLog)
        .containsExactly(
            "getTransactionTypeName",
            "getSyncIdentifier",
            "fetch",
            "convert:a",
            "delete",
            "save:1");
    assertThat(synchronizer.savedEntities).containsExactly(new TestEntity("a"));
    then(transactionManager).should().rollback(status);
    then(transactionManager).should(never()).commit(any());
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).hasSize(1);
    assertThat(result.get()).isEqualTo(new SyncResult("test-type", "test-id", 0, 0));
  }

  @Test
  void syncIdentifierFailurePropagatesUncaughtBeforeFetchWithNoErrorLog() {
    synchronizer.syncIdentifierFailure = new IllegalStateException("no identifier");

    assertThatThrownBy(() -> synchronizer.triggerSync(context))
        .isSameAs(synchronizer.syncIdentifierFailure);

    assertThat(synchronizer.callLog).containsExactly("getTransactionTypeName", "getSyncIdentifier");
    then(transactionManager).shouldHaveNoInteractions();
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).isEmpty();
  }

  @Test
  void transactionTypeNameFailurePropagatesUncaughtBeforeSyncIdentifierIsRequested() {
    synchronizer.transactionTypeNameFailure = new IllegalStateException("no type name");

    assertThatThrownBy(() -> synchronizer.triggerSync(context))
        .isSameAs(synchronizer.transactionTypeNameFailure);

    assertThat(synchronizer.callLog).containsExactly("getTransactionTypeName");
    then(transactionManager).shouldHaveNoInteractions();
    assertThat(logAppender.list).filteredOn(e -> e.getLevel() == Level.ERROR).isEmpty();
  }

  @Test
  void nowReturnsCurrentTimeFromClockHolder() {
    assertThat(synchronizer.callNow()).isEqualTo(testLocalDateTime);
  }

  private record TestEntity(String value) {}

  private static class RecordingTransactionSynchronizer
      extends AbstractTransactionSynchronizer<String, TestEntity> {

    final List<String> callLog = new ArrayList<>();
    final List<SyncContext> receivedContexts = new ArrayList<>();
    List<String> transactionsToFetch = List.of();
    List<TestEntity> savedEntities = List.of();
    int deleteResult = 0;
    RuntimeException fetchFailure;
    RuntimeException convertFailure;
    RuntimeException deleteFailure;
    RuntimeException saveFailure;
    RuntimeException syncIdentifierFailure;
    RuntimeException transactionTypeNameFailure;

    RecordingTransactionSynchronizer(
        EpisService episService, TransactionTemplate transactionTemplate) {
      super(episService, transactionTemplate);
    }

    SyncResult triggerSync(SyncContext context) {
      return syncInternal(context);
    }

    LocalDateTime callNow() {
      return now();
    }

    @Override
    protected List<String> fetchTransactions(SyncContext context) {
      callLog.add("fetch");
      receivedContexts.add(context);
      if (fetchFailure != null) {
        throw fetchFailure;
      }
      return transactionsToFetch;
    }

    @Override
    protected int deleteExistingTransactions(SyncContext context) {
      callLog.add("delete");
      receivedContexts.add(context);
      if (deleteFailure != null) {
        throw deleteFailure;
      }
      return deleteResult;
    }

    @Override
    protected TestEntity convertToEntity(String dto, SyncContext context) {
      callLog.add("convert:" + dto);
      receivedContexts.add(context);
      if (convertFailure != null) {
        throw convertFailure;
      }
      return new TestEntity(dto);
    }

    @Override
    protected void saveEntities(List<TestEntity> entities) {
      callLog.add("save:" + entities.size());
      savedEntities = entities;
      if (saveFailure != null) {
        throw saveFailure;
      }
    }

    @Override
    protected String getTransactionTypeName() {
      callLog.add("getTransactionTypeName");
      if (transactionTypeNameFailure != null) {
        throw transactionTypeNameFailure;
      }
      return "test-type";
    }

    @Override
    protected String getSyncIdentifier(SyncContext context) {
      callLog.add("getSyncIdentifier");
      receivedContexts.add(context);
      if (syncIdentifierFailure != null) {
        throw syncIdentifierFailure;
      }
      return "test-id";
    }
  }
}
