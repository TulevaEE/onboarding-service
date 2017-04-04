package ee.tuleva.onboarding.mandate.processor;

import ee.tuleva.onboarding.mandate.Mandate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "mandate_process")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class MandateProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "mandate_id", nullable = false)
    private Mandate mandate;

    private String processId;

    private Boolean successful;

    private Integer errorCode;

    @NotNull
    private Instant createdDate;

    @PrePersist
    protected void onCreate() {
        createdDate = Instant.now();
    }

    public Optional<Boolean> isSuccessful() {
        return Optional.ofNullable(successful);
    }

    public Optional<Boolean> getSuccessful() {
        return Optional.ofNullable(successful);
    }

    public Optional<Integer> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }

}
