package ee.tuleva.onboarding.analytics.thirdpillar;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.user.personalcode.ValidPersonalCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "third_pillar", schema = "analytics")
public class AnalyticsThirdPillar implements Person {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ValidPersonalCode
  @Column(name = "personal_id")
  private String personalCode;

  private String firstName;

  private String lastName;

  private String phoneNo;

  private String email;

  private String country;

  private LocalDateTime reportingDate;

  private LocalDateTime dateCreated;
}
