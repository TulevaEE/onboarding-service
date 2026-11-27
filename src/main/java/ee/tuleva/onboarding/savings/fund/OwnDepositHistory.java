package ee.tuleva.onboarding.savings.fund;

import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.party.PartyId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OwnDepositHistory {

  private final JdbcClient jdbcClient;

  public record FirstDeposit(String remitterIban, Instant recordedAt) {}

  public List<FirstDeposit> firstDepositPerRemitterIban(PartyId party, Instant recordedUntil) {
    return jdbcClient
        .sql(
            """
            SELECT remitter_iban, MIN(created_at) AS first_recorded_at
            FROM saving_fund_payment
            WHERE party_type = :partyType AND party_code = :partyCode
              AND remitter_id_code = :partyCode
              AND amount > 0
              AND remitter_iban IS NOT NULL
              AND created_at <= :recordedUntil
            GROUP BY remitter_iban
            """)
        .param("partyType", party.type().name())
        .param("partyCode", party.code())
        .param("recordedUntil", Timestamp.from(recordedUntil))
        .query(
            (rs, rowNum) ->
                new FirstDeposit(
                    requireNonNull(rs.getString("remitter_iban")),
                    rs.getTimestamp("first_recorded_at").toInstant()))
        .list();
  }
}
