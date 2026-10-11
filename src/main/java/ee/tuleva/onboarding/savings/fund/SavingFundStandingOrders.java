package ee.tuleva.onboarding.savings.fund;

import ee.tuleva.onboarding.party.PartyId;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavingFundStandingOrders {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public int countMonthsSince(PartyId partyId, LocalDate fromDate) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(distinct to_char(created_at, 'YYYY-MM')) from saving_fund_payment payment
            where party_type = :party_type and party_code = :party_code
              and status in ('ISSUED', 'PROCESSED')
              and created_at >= :from_date
              and not (
                regexp_like(coalesce(description, ''), '^[0-9]{8,11}, [0-9]{10}(, [A-Za-z0-9]+)?$')
                and not exists (
                  select 1 from saving_fund_payment other
                  where other.party_type = payment.party_type
                    and other.party_code = payment.party_code
                    and other.description = payment.description
                    and other.id <> payment.id
                    and other.status in ('ISSUED', 'PROCESSED')))
            """,
            Map.of(
                "party_type", partyId.type().name(),
                "party_code", partyId.code(),
                "from_date", Timestamp.valueOf(fromDate.atStartOfDay())),
            Integer.class);
    return count == null ? 0 : count;
  }
}
