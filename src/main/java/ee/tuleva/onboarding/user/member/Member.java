package ee.tuleva.onboarding.user.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.User;
import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Past;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@Entity
@Table(name = "member")
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"user"})
public class Member implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    private Integer memberNumber;

    @NotNull
    @Past
    private Instant createdDate;

}
