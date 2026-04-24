package ee.tuleva.onboarding.ledger;

import static jakarta.persistence.EnumType.STRING;
import static jakarta.persistence.GenerationType.UUID;
import static org.hibernate.generator.EventType.INSERT;
import static org.hibernate.type.SqlTypes.JSON;

import ee.tuleva.onboarding.auth.role.RoleType;
import ee.tuleva.onboarding.party.PartyId;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

@Entity
@Table(name = "party", schema = "ledger")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class LedgerParty {

  public enum PartyType {
    PERSON,
    LEGAL_ENTITY;

    public static PartyType from(RoleType roleType) {
      return valueOf(roleType.name());
    }

    public static PartyType from(PartyId.Type type) {
      return valueOf(type.name());
    }
  }

  @Id
  @GeneratedValue(strategy = UUID)
  private UUID id;

  @Enumerated(STRING)
  @Column(columnDefinition = "ledger.party_type")
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @NotNull
  private PartyType partyType;

  @NotNull private String ownerId;

  @JdbcTypeCode(JSON)
  @Column(nullable = false)
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
