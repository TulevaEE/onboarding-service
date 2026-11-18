package ee.tuleva.onboarding.investment.epis;

import java.time.LocalDate;

public record R16PaymentDates(
    LocalDate paymentMonth, LocalDate paymentDeadline, LocalDate sellByDate) {}
