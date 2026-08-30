package ee.tuleva.onboarding.investment.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TransactionParameters(
    List<ModelWeight> modelWeights,
    @Nullable LocalDate modelEffectiveDate,
    BigDecimal cashBuffer,
    BigDecimal minTransaction,
    Map<String, PositionLimitSnapshot> positionLimits,
    Set<String> fastSellIsins,
    Map<String, InstrumentType> instrumentTypes,
    Map<String, OrderVenue> orderVenues) {}
