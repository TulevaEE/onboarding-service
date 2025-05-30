package ee.tuleva.onboarding.ledger;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Type;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "party", schema = "ledger")
@Getter
@Builder
@AllArgsConstructor
public class LedgerParty {

  public LedgerParty() {}

  public enum PartyType {
    USER,
    LEGAL_ENTITY
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(nullable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, columnDefinition = "ledger.party_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  private PartyType type;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String
      ownerId; // TODO currently personal ID, can add party representative logic later for children

  // and companies

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  private Map<String, Object> details;

  @Column(columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false, insertable = false)
  private Instant createdAt;
}
