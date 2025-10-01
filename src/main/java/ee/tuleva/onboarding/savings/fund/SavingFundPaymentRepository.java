package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.CREATED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.FROZEN;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.PROCESSED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RECEIVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.time.temporal.ChronoUnit.DAYS;

import ee.tuleva.onboarding.currency.Currency;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
@RequiredArgsConstructor
public class SavingFundPaymentRepository {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public boolean existsByRemitterIdCodeAndDescription(String remitterIdCode, String description) {
    throw new UnsupportedOperationException("Change me!");
  }

  public void save(SavingFundPayment payment) {
    throw new UnsupportedOperationException("Change me!");
  }

  public UUID savePaymentData(SavingFundPayment payment) {
    var id = UUID.randomUUID();
    var parameters =
        createParameters(payment).addValue("id", id).addValue("status", CREATED.name());

    jdbcTemplate.update(
        """
      insert into saving_fund_payment (id, external_id, amount, currency, description, remitter_iban, remitter_id_code, remitter_name, beneficiary_iban, beneficiary_id_code, beneficiary_name, status)
      values (:id, :external_id, :amount, :currency, :description, :remitter_iban, :remitter_id_code, :remitter_name, :beneficiary_iban, :beneficiary_id_code, :beneficiary_name, :status)
      """,
        parameters);

    return id;
  }

  public void updatePaymentData(UUID paymentId, SavingFundPayment payment) {
    var parameters = createParameters(payment).addValue("id", paymentId);
    jdbcTemplate.update(
        """
        update saving_fund_payment
        set external_id=:external_id, amount=:amount, currency=:currency, description=:description, remitter_iban=:remitter_iban, remitter_id_code=:remitter_id_code,
            remitter_name=:remitter_name, beneficiary_iban=:beneficiary_iban, beneficiary_id_code=:beneficiary_id_code, beneficiary_name=:beneficiary_name
        where id=:id
        """,
        parameters);
  }

  public List<SavingFundPayment> findPaymentsWithStatus(SavingFundPayment.Status status) {
    return jdbcTemplate.query(
        """
        select * from saving_fund_payment where status=:status
        """,
        Map.of("status", status.name()),
        this::rowMapper);
  }

  public List<SavingFundPayment> findRecentPayments(String description) {
    return jdbcTemplate.query(
        """
        select * from saving_fund_payment where description=:description and created_at > :recent
        """,
        Map.of("description", description, "recent", Instant.now().minus(5, DAYS)),
        this::rowMapper);
  }

  private SavingFundPayment rowMapper(ResultSet rs, int ignored) throws SQLException {
    return SavingFundPayment.builder()
        .id(UUID.fromString(rs.getString("id")))
        .userId(rs.getObject("user_id", Long.class))
        .externalId(rs.getString("external_id"))
        .amount(rs.getBigDecimal("amount"))
        .currency(Currency.valueOf(rs.getString("currency")))
        .description(rs.getString("description"))
        .remitterIban(rs.getString("remitter_iban"))
        .remitterIdCode(rs.getString("remitter_id_code"))
        .remitterName(rs.getString("remitter_name"))
        .beneficiaryIban(rs.getString("beneficiary_iban"))
        .beneficiaryIdCode(rs.getString("beneficiary_id_code"))
        .beneficiaryName(rs.getString("beneficiary_name"))
        .createdAt(instant(rs, "created_at"))
        .statusChangedAt(instant(rs, "status_changed_at"))
        .cancelledAt(instant(rs, "cancelled_at"))
        .returnReason(rs.getString("return_reason"))
        .build();
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    var timestamp = rs.getTimestamp(column);
    return timestamp != null ? timestamp.toInstant() : null;
  }

  private MapSqlParameterSource createParameters(SavingFundPayment payment) {
    return new MapSqlParameterSource()
        .addValue("external_id", payment.getExternalId())
        .addValue("amount", payment.getAmount())
        .addValue("currency", payment.getCurrency().name())
        .addValue("description", payment.getDescription())
        .addValue("remitter_iban", payment.getRemitterIban())
        .addValue("remitter_id_code", payment.getRemitterIdCode())
        .addValue("remitter_name", payment.getRemitterName())
        .addValue("beneficiary_iban", payment.getBeneficiaryIban())
        .addValue("beneficiary_id_code", payment.getBeneficiaryIdCode())
        .addValue("beneficiary_name", payment.getBeneficiaryName());
  }

  public void changeStatus(UUID paymentId, SavingFundPayment.Status newStatus) {
    var currentStatus = getAndLockCurrentStatus(paymentId);
    var allowedTransitions =
        Map.of(
            CREATED, Set.of(RECEIVED),
            RECEIVED, Set.of(VERIFIED, FROZEN, TO_BE_RETURNED),
            VERIFIED, Set.of(RESERVED, TO_BE_RETURNED),
            RESERVED, Set.of(PROCESSED),
            PROCESSED, Set.of(),
            FROZEN, Set.of(),
            TO_BE_RETURNED, Set.of(RETURNED),
            RETURNED, Set.of());
    if (!allowedTransitions.getOrDefault(currentStatus, Set.of()).contains(newStatus))
      throw new IllegalStateException(
          "Payment status transition " + currentStatus + " to " + newStatus + " is not allowed");
    log.info("SavingFundPayment {} status change: {} -> {}", paymentId, currentStatus, newStatus);
    jdbcTemplate.update(
        "UPDATE saving_fund_payment SET status=:status, status_changed_at=NOW() WHERE id=:id",
        Map.of("id", paymentId, "status", newStatus.name()));
  }

  public void attachUser(UUID paymentId, Long userId) {
    var currentStatus = getAndLockCurrentStatus(paymentId);
    if (!Set.of(CREATED, RECEIVED).contains(currentStatus))
      throw new IllegalStateException(
          "Attaching of user ID is not allowed when payment is " + currentStatus);
    jdbcTemplate.update(
        "UPDATE saving_fund_payment SET user_id=:user_id WHERE id=:id",
        Map.of("id", paymentId, "user_id", userId));
  }

  public void addReturnReason(UUID paymentId, String reason) {
    jdbcTemplate.update(
        "UPDATE saving_fund_payment SET return_reason=:reason WHERE id=:id",
        Map.of("id", paymentId, "reason", reason));
  }

  public void cancel(UUID paymentId) {
    var currentStatus = getAndLockCurrentStatus(paymentId);
    if (!Set.of(CREATED, RECEIVED, VERIFIED).contains(currentStatus))
      throw new IllegalStateException("Cancellation not allowed when payment is " + currentStatus);
    jdbcTemplate.update(
        "UPDATE saving_fund_payment SET cancelled_at=NOW() WHERE id=:id", Map.of("id", paymentId));
  }

  private SavingFundPayment.Status getAndLockCurrentStatus(UUID paymentId) {
    return jdbcTemplate.queryForObject(
        "select status from saving_fund_payment where id=:id for update",
        Map.of("id", paymentId),
        SavingFundPayment.Status.class);
  }

  // todo
  // Montonio should use findRecentPayments(), savePaymentData() and attachUser()

  // Swedbank should use findRecentPayments(), savePaymentData() OR updatePaymentData() and
  // changeStatus(RECEIVED)

  // sanctions check & identity check should be in a single job which always ends with
  // changeStatus(...)

  // reservation job should check the cancelledAt timestamp and if set must call
  // changeStatus(TO_BE_RETURNED)
}
