package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.FAILED;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.PROCESSING;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUV100;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.inOrder;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransactionCommandRunnerTest {

  private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-10");

  @Mock private TransactionCommandRepository commandRepository;
  @Mock private TransactionAuditEventRepository auditEventRepository;
  @Mock private TransactionPreparationService preparationService;
  @Mock private TradePrepInputGateNotifier inputGateNotifier;
  @Mock private EntityManager entityManager;

  @InjectMocks private TransactionCommandRunner runner;

  private static TransactionBatch batch(Long id, BatchStatus status) {
    return TransactionBatch.builder()
        .id(id)
        .fund(TUK75)
        .status(status)
        .createdBy("system")
        .createdAt(Instant.parse("2026-06-10T09:00:00Z"))
        .metadata(Map.of())
        .build();
  }

  private static TransactionOrder order(Long id, TransactionBatch batch) {
    return TransactionOrder.builder()
        .id(id)
        .batch(batch)
        .fund(TUK75)
        .instrumentIsin("IE00BFG1TM61")
        .transactionType(BUY)
        .instrumentType(ETF)
        .orderAmount(new BigDecimal("1000.00"))
        .orderVenue(OrderVenue.SEB)
        .build();
  }

  @Test
  void createAndProcess_persistsProcessingCommandAndProcessesItSynchronously() {
    TransactionBatch batch = batch(10L, DRAFT);
    TransactionOrder order = order(100L, batch);
    willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              assertThat(command.getStatus()).isEqualTo(PROCESSING);
              assertThat(command.getManualAdjustments())
                  .isEqualTo(Map.of("IE00BFG1TM61", "1000.00"));
              command.setStatus(CALCULATED);
              command.setBatchId(10L);
              return new ProcessCommandResult(batch, List.of(order));
            })
        .given(preparationService)
        .processCommand(any());

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75, REBALANCE, AS_OF_DATE, Map.of("IE00BFG1TM61", "1000.00"), null, null, null),
            "operator-9");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of("IE00BFG1TM61", "1000.00"))
                .actor("operator-9")
                .status(CALCULATED)
                .batchId(10L)
                .build());
    assertThat(response.fund()).isEqualTo(TUK75);
    assertThat(response.status()).isEqualTo(CALCULATED);
    assertThat(response.batchId()).isEqualTo(10L);
    assertThat(response.orders()).containsExactly(TransactionOrderResponse.from(order));
  }

  @Test
  void createAndProcess_postsTheGateStopOnlyAfterTheCommandHasBeenProcessed() {
    var stop = stopped(TUK75);
    given(preparationService.processCommand(any())).willReturn(stop);

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75, REBALANCE, AS_OF_DATE, null, null, null, null),
            "admin");

    InOrder inOrder = inOrder(preparationService, inputGateNotifier);
    then(preparationService).should(inOrder).processCommand(any());
    then(inputGateNotifier).should(inOrder).notifyStopped(List.of(stop));
    assertThat(response.orders()).isEmpty();
  }

  @Test
  void createAndProcessAll_stillPostsTheStopsAlreadyMadeWhenALaterFundsCommandCannotBeSaved() {
    var tuk75 = stopped(TUK75);
    given(preparationService.processCommand(any())).willReturn(tuk75);
    willAnswer(invocation -> invocation.getArgument(0))
        .willThrow(new IllegalStateException("Database unavailable"))
        .given(commandRepository)
        .save(any());

    assertThatThrownBy(
            () ->
                runner.createAndProcessAll(
                    List.of(TUK75, TUK00), REBALANCE, AS_OF_DATE, "admin", Map.of()))
        .isInstanceOf(IllegalStateException.class);

    then(inputGateNotifier).should().notifyStopped(List.of(tuk75));
  }

  @Test
  void createAndProcessAll_postsOneMessageWithEveryFundTheGatesStoppedAndNoneThatMadeADraft() {
    var tuk75 = stopped(TUK75);
    var tuk00 = stopped(TUK00);
    given(preparationService.processCommand(any()))
        .willAnswer(
            invocation ->
                switch (invocation.<TransactionCommand>getArgument(0).getFund()) {
                  case TUK75 -> tuk75;
                  case TUK00 -> tuk00;
                  default -> new ProcessCommandResult(batch(10L, DRAFT), List.of());
                });

    runner.createAndProcessAll(null, REBALANCE, AS_OF_DATE, "admin", Map.of());

    then(inputGateNotifier).should().notifyStopped(List.of(tuk75, tuk00));
  }

  private static CommandOutcome.StoppedByInputGates stopped(TulevaFund fund) {
    return new CommandOutcome.StoppedByInputGates(
        fund, AS_OF_DATE, List.of(new InputGateFailure.NoR45Loaded(AS_OF_DATE)));
  }

  @Test
  void createAndProcessAll_startsEveryFundsCommandFromAnEmptyPersistenceContext() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    runner.createAndProcessAll(List.of(TUK75, TUK00), REBALANCE, AS_OF_DATE, "admin", Map.of());

    InOrder inOrder = inOrder(entityManager, commandRepository, preparationService);
    then(entityManager).should(inOrder).clear();
    then(commandRepository).should(inOrder).save(any());
    then(preparationService).should(inOrder).processCommand(any());
    then(entityManager).should(inOrder).clear();
    then(commandRepository).should(inOrder).save(any());
    then(preparationService).should(inOrder).processCommand(any());
  }

  @Test
  void createAndProcess_withNullAdjustments_defaultsToEmptyMap() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    runner.createAndProcess(
        new CreateTransactionCommandRequest(TUK75, REBALANCE, AS_OF_DATE, null, null, null, null),
        "admin");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of())
                .actor("admin")
                .status(PROCESSING)
                .build());
  }

  @Test
  void createAndProcess_withCashOverride_persistsCashAndItsCommentOnCommand() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    runner.createAndProcess(
        new CreateTransactionCommandRequest(
            TUK75,
            REBALANCE,
            AS_OF_DATE,
            null,
            new CashOverride(new BigDecimal("40000.00"), "Gateway balance at 10:15"),
            null,
            null),
        "operator-9");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of())
                .cash(new BigDecimal("40000.00"))
                .cashComment("Gateway balance at 10:15")
                .actor("operator-9")
                .status(PROCESSING)
                .build());
  }

  @Test
  void createAndProcess_withExcludedInstruments_persistsThemOnTheCommandAndReturnsThem() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75,
                REBALANCE,
                AS_OF_DATE,
                null,
                null,
                Set.of("IE00BMDBMY19", "IE00BFG1TM61"),
                null),
            "operator-9");

    then(commandRepository)
        .should()
        .save(
            TransactionCommand.builder()
                .fund(TUK75)
                .mode(REBALANCE)
                .asOfDate(AS_OF_DATE)
                .manualAdjustments(Map.of())
                .excludeIsins(Set.of("IE00BMDBMY19", "IE00BFG1TM61"))
                .actor("operator-9")
                .status(PROCESSING)
                .build());
    assertThat(response.excludeIsins()).containsExactly("IE00BFG1TM61", "IE00BMDBMY19");
  }

  @Test
  void createAndProcess_failedProcessingStillReturnsTheCashOverrideAndItsComment() {
    willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              command.setStatus(FAILED);
              return new CommandOutcome.Failed();
            })
        .given(preparationService)
        .processCommand(any());

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75,
                REBALANCE,
                AS_OF_DATE,
                null,
                new CashOverride(new BigDecimal("40000.00"), "Gateway balance at 10:15"),
                null,
                null),
            "admin");

    assertThat(response.cash()).isEqualByComparingTo("40000.00");
    assertThat(response.cashComment()).isEqualTo("Gateway balance at 10:15");
  }

  @Test
  void createAndProcess_failedProcessingReturnsFailedCommandWithoutOrders() {
    willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              command.setStatus(FAILED);
              command.setErrorMessage("No positions found");
              return new CommandOutcome.Failed();
            })
        .given(preparationService)
        .processCommand(any());

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75, REBALANCE, AS_OF_DATE, null, null, null, null),
            "admin");

    assertThat(response.status()).isEqualTo(FAILED);
    assertThat(response.errorMessage()).isEqualTo("No positions found");
    assertThat(response.orders()).isEmpty();
  }

  @Test
  void createAndProcessAll_withoutFunds_processesEveryFund() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    List<TransactionCommandResponse> responses =
        runner.createAndProcessAll(null, REBALANCE, AS_OF_DATE, "admin", Map.of());

    assertThat(responses)
        .extracting(TransactionCommandResponse::fund)
        .containsExactly(TulevaFund.values());
  }

  @Test
  void createAndProcessAll_withFunds_processesOnlyThoseFunds() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    List<TransactionCommandResponse> responses =
        runner.createAndProcessAll(List.of(TUK75), REBALANCE, AS_OF_DATE, "admin", Map.of());

    assertThat(responses).extracting(TransactionCommandResponse::fund).containsExactly(TUK75);
  }

  @Test
  void createAndProcessAll_passesPerFundCashOverrides() {
    given(preparationService.processCommand(any()))
        .willReturn(new ProcessCommandResult(batch(10L, DRAFT), List.of()));

    runner.createAndProcessAll(
        List.of(TUK75, TUV100),
        REBALANCE,
        AS_OF_DATE,
        "admin",
        Map.of(TUK75, new CashOverride(new BigDecimal("40000"), "Gateway balance at 10:15")));

    then(commandRepository)
        .should()
        .save(
            argThat(
                command ->
                    command.getFund() == TUK75
                        && new BigDecimal("40000").compareTo(command.getCash()) == 0
                        && "Gateway balance at 10:15".equals(command.getCashComment())));
    then(commandRepository)
        .should()
        .save(argThat(command -> command.getFund() == TUV100 && command.getCash() == null));
  }

  @Test
  void createAndProcessAll_cashOverrideForFundNotInBatch_throwsBadRequest() {
    assertThatThrownBy(
            () ->
                runner.createAndProcessAll(
                    List.of(TUK75),
                    REBALANCE,
                    AS_OF_DATE,
                    "admin",
                    Map.of(TUV100, new CashOverride(BigDecimal.ONE, "Gateway balance at 10:15"))))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
        .isEqualTo(400);
    then(commandRepository).shouldHaveNoInteractions();
  }

  @Test
  void createAndProcess_responseCarriesCalculationSnapshot() {
    Map<String, Object> payload = Map.of("summary", Map.of("tradeCount", "0"));
    given(preparationService.processCommand(any()))
        .willAnswer(
            invocation -> {
              TransactionCommand command = invocation.getArgument(0);
              command.setBatchId(10L);
              return new ProcessCommandResult(batch(10L, DRAFT), List.of());
            });
    given(auditEventRepository.findByBatchIdOrderByCreatedAt(10L))
        .willReturn(
            List.of(
                TransactionAuditEvent.builder()
                    .eventType("CALCULATION_COMPLETED")
                    .payload(payload)
                    .build()));

    TransactionCommandResponse response =
        runner.createAndProcess(
            new CreateTransactionCommandRequest(
                TUK75, REBALANCE, AS_OF_DATE, null, null, null, null),
            "admin");

    assertThat(response.calculationSnapshot()).isEqualTo(payload);
  }
}
