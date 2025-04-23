package ee.tuleva.onboarding.epis.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitOwnerDto {
  private String personId;
  private String firstName;
  private String name;
  private String phone;
  private String email;
  private String country;
  private String languagePreference;
  private String pensionAccount;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
  private LocalDate deathDate;

  private Pillar2DetailsDto pillar2Details;
  private Pillar3DetailsDto pillar3Details;
  private List<UnitOwnerBalanceDto> balances;
  private String fundManager;
}
