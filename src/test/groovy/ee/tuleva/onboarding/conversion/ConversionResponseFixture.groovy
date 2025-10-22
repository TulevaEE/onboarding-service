package ee.tuleva.onboarding.conversion

class ConversionResponseFixture {

  static ConversionResponse notConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .transfersPartial(false)
            .selectionComplete(false)
            .selectionPartial(false)
            .weightedAverageFee(0.0051)
            .build())
        .thirdPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(false)
            .transfersPartial(false)
            .selectionComplete(false)
            .selectionPartial(false)
            .weightedAverageFee(0.0052)
            .build())
        .build()
  }

  static ConversionResponse notFullyConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(false)
            .selectionPartial(false)
            .weightedAverageFee(0.0051)
            .build())
        .thirdPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(false)
            .selectionPartial(false)
            .weightedAverageFee(0.0052)
            .build())
        .build()
  }

  static ConversionResponse fullyConverted() {
    return ConversionResponse.builder()
        .secondPillar(ConversionResponse.Conversion.builder()
            .transfersComplete(true)
            .transfersPartial(true)
            .selectionComplete(true)
            .selectionPartial(true)
            .weightedAverageFee(0.0049)
            .build()
        ).thirdPillar(ConversionResponse.Conversion.builder()
        .transfersComplete(true)
        .transfersPartial(true)
        .selectionComplete(true)
        .selectionPartial(true)
        .weightedAverageFee(0.0048)
        .build()
    ).build()
  }

}
