package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.investment.transaction.BatchStatus.SENT;
import static ee.tuleva.onboarding.investment.transaction.CommandStatus.CALCULATED;
import static ee.tuleva.onboarding.investment.transaction.InstrumentType.ETF;
import static ee.tuleva.onboarding.investment.transaction.OrderStatus.DRAFT;
import static ee.tuleva.onboarding.investment.transaction.OrderVenue.SEB;
import static ee.tuleva.onboarding.investment.transaction.TransactionMode.REBALANCE;
import static ee.tuleva.onboarding.investment.transaction.TransactionType.BUY;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TUK75;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(TransactionCommandController.class)
@TestPropertySource(
    properties = {
      "admin.api-token=valid-token",
      "admin.operator-tokens.operator-7=operator-7-token"
    })
@Import({AdminTokenAuthenticator.class, AdminTokenConfiguration.class})
@WithMockUser
class TransactionCommandControllerTest {

  private static final LocalDate AS_OF_DATE = LocalDate.parse("2026-06-10");
  private static final UUID ORDER_UUID = UUID.fromString("3f29c4a7-1f7e-4b46-9c20-1111aaaa2222");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TransactionAdminService adminService;

  private static TransactionOrderResponse orderResponse() {
    return orderResponse(OrderType.MOC);
  }

  private static TransactionOrderResponse orderResponse(OrderType orderType) {
    return new TransactionOrderResponse(
        100L,
        "IE00BFG1TM61",
        BUY,
        ETF,
        new BigDecimal("1000.00"),
        new BigDecimal("8.500000"),
        SEB,
        DRAFT,
        orderType,
        ORDER_UUID,
        null,
        "operator note");
  }

  private static TransactionCommandResponse commandResponse() {
    return new TransactionCommandResponse(
        1L, TUK75, REBALANCE, AS_OF_DATE, CALCULATED, null, 10L, List.of(orderResponse()), null);
  }

  private static TransactionBatchResponse batchResponse() {
    return new TransactionBatchResponse(
        10L,
        TUK75,
        SENT,
        "system",
        Instant.parse("2026-06-10T09:00:00Z"),
        List.of("sebEtfXlsx", "xlsxExport"),
        Map.of("sebEtfXlsx", "https://drive.google.com/file/d/abc"),
        List.of(orderResponse()),
        Map.of("summary", Map.of("tradeCount", 1)));
  }

  @Test
  void createCommand_withValidToken_processesSynchronouslyAndReturnsResult() throws Exception {
    given(
            adminService.createAndProcess(
                TUK75,
                REBALANCE,
                AS_OF_DATE,
                Map.of("IE00BFG1TM61", "1000.00"),
                "shared-admin-token",
                null))
        .willReturn(commandResponse());

    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {
                      "fund": "TUK75",
                      "mode": "REBALANCE",
                      "asOfDate": "2026-06-10",
                      "manualAdjustments": {"IE00BFG1TM61": "1000.00"}
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.fund").value("TUK75"))
        .andExpect(jsonPath("$.status").value("CALCULATED"))
        .andExpect(jsonPath("$.batchId").value(10))
        .andExpect(jsonPath("$.orders[0].instrumentIsin").value("IE00BFG1TM61"))
        .andExpect(jsonPath("$.orders[0].orderAmount").value(1000.00));
  }

  @Test
  void createCommand_passesAdminActorToService() throws Exception {
    given(adminService.createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null, "operator-7", null))
        .willReturn(commandResponse());

    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isOk());

    then(adminService)
        .should()
        .createAndProcess(TUK75, REBALANCE, AS_OF_DATE, null, "operator-7", null);
  }

  @Test
  void createCommand_passesCashOverrideToService() throws Exception {
    given(
            adminService.createAndProcess(
                TUK75, REBALANCE, AS_OF_DATE, null, "shared-admin-token", new BigDecimal("40000")))
        .willReturn(commandResponse());

    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "mode": "REBALANCE", "asOfDate": "2026-06-10", "cash": 40000}
                    """))
        .andExpect(status().isOk());

    then(adminService)
        .should()
        .createAndProcess(
            TUK75, REBALANCE, AS_OF_DATE, null, "shared-admin-token", new BigDecimal("40000"));
  }

  @Test
  void createCommand_negativeCash_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "mode": "REBALANCE", "asOfDate": "2026-06-10", "cash": -1}
                    """))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void createCommands_negativeCashInMap_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-commands/batch")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"funds": ["TUK75"], "mode": "REBALANCE", "asOfDate": "2026-06-10",
                     "cash": {"TUK75": -1}}
                    """))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void createCommand_withInvalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createCommand_withTokenDifferingInLastCharacter_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "valid-tokeX")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"fund": "TUK75", "mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createCommand_withMissingFund_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-commands")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createCommandBatch_processesRequestedFunds() throws Exception {
    given(
            adminService.createAndProcessAll(
                List.of(TUK75, TUK00), REBALANCE, AS_OF_DATE, "shared-admin-token", Map.of()))
        .willReturn(
            List.of(
                commandResponse(),
                new TransactionCommandResponse(
                    2L, TUK00, REBALANCE, AS_OF_DATE, CALCULATED, null, 11L, List.of(), null)));

    mockMvc
        .perform(
            post("/admin/transaction-commands/batch")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"funds": ["TUK75", "TUK00"], "mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fund").value("TUK75"))
        .andExpect(jsonPath("$[1].fund").value("TUK00"));
  }

  @Test
  void createCommandBatch_withoutFunds_processesAllFunds() throws Exception {
    given(
            adminService.createAndProcessAll(
                null, REBALANCE, AS_OF_DATE, "shared-admin-token", Map.of()))
        .willReturn(List.of(commandResponse()));

    mockMvc
        .perform(
            post("/admin/transaction-commands/batch")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"mode": "REBALANCE", "asOfDate": "2026-06-10"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].fund").value("TUK75"));
  }

  @Test
  void getCommand_returnsCommandWithCalculationOutcome() throws Exception {
    given(adminService.getCommand(1L)).willReturn(Optional.of(commandResponse()));

    mockMvc
        .perform(get("/admin/transaction-commands/1").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.orders[0].orderUuid").value(ORDER_UUID.toString()))
        .andExpect(jsonPath("$.orders[0].comment").value("operator note"));
  }

  @Test
  void getCommand_unknownId_returnsNotFound() throws Exception {
    given(adminService.getCommand(999L)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/admin/transaction-commands/999").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void getBatch_returnsBatchWithOrdersAndExportReferences() throws Exception {
    given(adminService.getBatch(10L)).willReturn(Optional.of(batchResponse()));

    mockMvc
        .perform(get("/admin/transaction-batches/10").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.availableExports[0]").value("sebEtfXlsx"))
        .andExpect(
            jsonPath("$.driveFileUrls.sebEtfXlsx").value("https://drive.google.com/file/d/abc"))
        .andExpect(jsonPath("$.orders[0].instrumentIsin").value("IE00BFG1TM61"))
        .andExpect(jsonPath("$.calculationSnapshot.summary.tradeCount").value(1));
  }

  @Test
  void getBatch_unknownId_returnsNotFound() throws Exception {
    given(adminService.getBatch(999L)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/admin/transaction-batches/999").header("X-Admin-Token", "valid-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void confirmBatch_finalizesAndReturnsBatch() throws Exception {
    given(adminService.confirmAndFinalize(10L, "shared-admin-token")).willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/confirm")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10))
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  void confirmBatch_passesAdminActorToService() throws Exception {
    given(adminService.confirmAndFinalize(10L, "operator-7")).willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/confirm")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token"))
        .andExpect(status().isOk());

    then(adminService).should().confirmAndFinalize(10L, "operator-7");
  }

  @Test
  void confirmBatch_notAwaitingConfirmation_returnsConflict() throws Exception {
    given(adminService.confirmAndFinalize(10L, "shared-admin-token"))
        .willThrow(
            new ResponseStatusException(
                CONFLICT, "Batch not awaiting confirmation: id=10, status=SENT"));

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/confirm")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isConflict());
  }

  @Test
  void cancelBatch_withReasonAndActor_invokesServiceAndReturnsBatch() throws Exception {
    given(adminService.cancelBatch(10L, "duplicate batch", "operator-7"))
        .willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/cancel")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "duplicate batch"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10));

    then(adminService).should().cancelBatch(10L, "duplicate batch", "operator-7");
  }

  @Test
  void cancelBatch_withoutActor_defaultsToAdmin() throws Exception {
    given(adminService.cancelBatch(10L, "duplicate batch", "shared-admin-token"))
        .willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/cancel")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "duplicate batch"}
                    """))
        .andExpect(status().isOk());

    then(adminService).should().cancelBatch(10L, "duplicate batch", "shared-admin-token");
  }

  @Test
  void cancelBatch_blankReason_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-batches/10/cancel")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "  "}
                    """))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void cancelBatch_invalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-batches/10/cancel")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "duplicate batch"}
                    """))
        .andExpect(status().isUnauthorized());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void discardBatch_draftBatch_invokesServiceAndReturnsBatch() throws Exception {
    given(adminService.discardBatch(10L, "operator-7")).willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/discard")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(10));

    then(adminService).should().discardBatch(10L, "operator-7");
  }

  @Test
  void discardBatch_withoutActor_defaultsToAdmin() throws Exception {
    given(adminService.discardBatch(10L, "shared-admin-token")).willReturn(batchResponse());

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/discard")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk());

    then(adminService).should().discardBatch(10L, "shared-admin-token");
  }

  @Test
  void discardBatch_nonDraftBatch_returnsConflict() throws Exception {
    given(adminService.discardBatch(10L, "shared-admin-token"))
        .willThrow(
            new ResponseStatusException(CONFLICT, "Batch not in DRAFT status: id=10, status=SENT"));

    mockMvc
        .perform(
            post("/admin/transaction-batches/10/discard")
                .with(csrf())
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isConflict());
  }

  @Test
  void discardBatch_invalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-batches/10/discard")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token"))
        .andExpect(status().isUnauthorized());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void cancelOrder_withReasonAndActor_invokesServiceAndReturnsOrder() throws Exception {
    given(adminService.cancelOrder(100L, "trader error", "operator-7")).willReturn(orderResponse());

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/cancel")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "trader error"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(100));

    then(adminService).should().cancelOrder(100L, "trader error", "operator-7");
  }

  @Test
  void cancelOrder_withoutActor_defaultsToAdmin() throws Exception {
    given(adminService.cancelOrder(100L, "trader error", "shared-admin-token"))
        .willReturn(orderResponse());

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/cancel")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "trader error"}
                    """))
        .andExpect(status().isOk());

    then(adminService).should().cancelOrder(100L, "trader error", "shared-admin-token");
  }

  @Test
  void setOrderType_validType_invokesServiceAndReturnsOrder() throws Exception {
    given(adminService.setOrderType(100L, OrderType.NAV, "operator-7"))
        .willReturn(orderResponse(OrderType.NAV));

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/order-type")
                .with(csrf())
                .header("X-Admin-Token", "operator-7-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"orderType": "NAV"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(100))
        .andExpect(jsonPath("$.orderType").value("NAV"));

    then(adminService).should().setOrderType(100L, OrderType.NAV, "operator-7");
  }

  @Test
  void setOrderType_withoutActor_defaultsToAdmin() throws Exception {
    given(adminService.setOrderType(100L, OrderType.RISK, "shared-admin-token"))
        .willReturn(orderResponse(OrderType.RISK));

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/order-type")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"orderType": "RISK"}
                    """))
        .andExpect(status().isOk());

    then(adminService).should().setOrderType(100L, OrderType.RISK, "shared-admin-token");
  }

  @Test
  void setOrderType_unknownType_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-orders/100/order-type")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"orderType": "VWAP"}
                    """))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void setOrderType_missingType_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-orders/100/order-type")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void setOrderType_notDraftOrder_returnsConflict() throws Exception {
    given(adminService.setOrderType(100L, OrderType.NAV, "shared-admin-token"))
        .willThrow(
            new ResponseStatusException(CONFLICT, "Only DRAFT orders can change order type"));

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/order-type")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"orderType": "NAV"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void cancelOrder_blankReason_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-orders/100/cancel")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "  "}
                    """))
        .andExpect(status().isBadRequest());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void cancelOrder_notSentOrder_returnsConflict() throws Exception {
    given(adminService.cancelOrder(100L, "trader error", "shared-admin-token"))
        .willThrow(
            new ResponseStatusException(
                CONFLICT, "Only SENT orders can be cancelled: id=100, status=DRAFT"));

    mockMvc
        .perform(
            post("/admin/transaction-orders/100/cancel")
                .with(csrf())
                .header("X-Admin-Token", "valid-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "trader error"}
                    """))
        .andExpect(status().isConflict());
  }

  @Test
  void cancelOrder_invalidToken_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post("/admin/transaction-orders/100/cancel")
                .with(csrf())
                .header("X-Admin-Token", "wrong-token")
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"reason": "trader error"}
                    """))
        .andExpect(status().isUnauthorized());

    then(adminService).shouldHaveNoInteractions();
  }

  @Test
  void downloadExport_returnsXlsxFile() throws Exception {
    byte[] xlsx = {1, 2, 3};
    given(adminService.exportFile(10L, "sebEtfXlsx")).willReturn(Optional.of(xlsx));

    mockMvc
        .perform(
            get("/admin/transaction-batches/10/exports/sebEtfXlsx")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"batch-10-sebEtfXlsx.xlsx\""))
        .andExpect(content().bytes(xlsx));
  }

  @Test
  void downloadExport_missingExport_returnsNotFound() throws Exception {
    given(adminService.exportFile(10L, "sebEtfXlsx")).willReturn(Optional.empty());

    mockMvc
        .perform(
            get("/admin/transaction-batches/10/exports/sebEtfXlsx")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isNotFound());
  }

  @Test
  void downloadExport_sebFundOrderFile_isServedAsCsv() throws Exception {
    var csv = "Original reference;Client name\n".getBytes(UTF_8);
    given(adminService.exportFile(10L, "sebFundXlsx")).willReturn(Optional.of(csv));

    mockMvc
        .perform(
            get("/admin/transaction-batches/10/exports/sebFundXlsx")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"batch-10-sebFundXlsx.csv\""))
        .andExpect(content().bytes(csv));
  }

  @Test
  void downloadExport_unknownExportType_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            get("/admin/transaction-batches/10/exports/notAnExport")
                .header("X-Admin-Token", "valid-token"))
        .andExpect(status().isNotFound());

    then(adminService).shouldHaveNoInteractions();
  }
}
