package ee.tuleva.onboarding.user.member;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ee.tuleva.onboarding.user.User;
import lombok.*;
import org.hibernate.annotations.Where;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@Entity
@Table(name = "member")
@AllArgsConstructor
@NoArgsConstructor
@ToString(exclude = {"user"})
@Where(clause = "active = true")
public class Member implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    private Integer memberNumber;

    @NotNull
    private Instant createdDate;

    @NotNull
    @Builder.Default
    private Boolean active = true;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now();
    }

}
