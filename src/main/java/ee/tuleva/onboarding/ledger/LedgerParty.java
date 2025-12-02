package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;
import static org.hibernate.generator.EventType.INSERT;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.Type;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "party", schema = "ledger")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LedgerParty {

  public enum PartyType {
    USER,
    LEGAL_ENTITY
  }

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.party_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private PartyType partyType;

  @NotNull
  // TODO currently personal ID, can add party representative logic later for children and companies
  private String ownerId;

  @Type(JsonType.class)
  @Column(columnDefinition = "JSONB", nullable = false)
  @NotNull
  private Map<String, Object> details;

  @Column(nullable = false, updatable = false, insertable = false)
  @Generated(event = INSERT)
  private Instant createdAt;

  @Builder
  public LedgerParty(PartyType partyType, String ownerId, Map<String, Object> details) {
    this.partyType = partyType;
    this.ownerId = ownerId;
    this.details = details;
  }
}
