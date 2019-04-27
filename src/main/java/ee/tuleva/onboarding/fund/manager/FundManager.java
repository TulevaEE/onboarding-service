package ee.tuleva.onboarding.fund.manager;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;

@Data
@Builder
@Entity
@Table(name = "fund_manager")
@AllArgsConstructor
@NoArgsConstructor
public class FundManager {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    String name;

}
