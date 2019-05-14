package ee.tuleva.onboarding.aml;

import ee.tuleva.onboarding.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AmlCheck {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @Enumerated(value = EnumType.STRING)
    private AmlCheckType type;

    private boolean success;

    @CreatedDate
    private Instant createdTime;

    @PrePersist
    protected void onCreate() {
        createdTime = Instant.now();
    }
}
