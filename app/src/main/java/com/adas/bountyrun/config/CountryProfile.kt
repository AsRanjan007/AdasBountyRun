package com.adas.bountyrun.config

/** Which side of the road traffic drives on (spec §4). */
enum class DrivingSide { LEFT, RIGHT }

/** Speed unit displayed on the HUD; gameplay is always metric internally. */
enum class SpeedUnit(val label: String, val fromKmh: (Float) -> Float) {
    KMH("km/h", { it }),
    MPH("mph", { it * 0.621371f })
}

/**
 * Country configuration (spec §4). Controls traffic direction, steering side,
 * units, the mix of local road users and typical hazards. The India profile is
 * fully fleshed out per the specification; other countries provide sensible
 * defaults and can be extended the same way.
 */
data class CountryProfile(
    val code: String,
    val displayName: String,
    val drivingSide: DrivingSide,
    val speedUnit: SpeedUnit,
    /** Steering wheel on the right for left-side traffic (e.g. India, UK, Japan). */
    val rightHandDrive: Boolean,
    /** Relative spawn weights for local vehicle flavour. */
    val vehicleMix: Map<LocalVehicle, Float>,
    /** Extra hazards common to this country. */
    val commonHazards: List<HazardKind>,
    /** Chaotic-crossing factor 0f..1f: higher = more unpredictable pedestrians. */
    val pedestrianUnpredictability: Float
) {
    companion object {
        val INDIA = CountryProfile(
            code = "IN",
            displayName = "India",
            drivingSide = DrivingSide.LEFT,
            speedUnit = SpeedUnit.KMH,
            rightHandDrive = true,
            vehicleMix = mapOf(
                LocalVehicle.CAR to 1.0f,
                LocalVehicle.MOTORCYCLE to 1.3f,
                LocalVehicle.SCOOTER to 1.0f,
                LocalVehicle.AUTO_RICKSHAW to 1.1f,
                LocalVehicle.E_RICKSHAW to 0.7f,
                LocalVehicle.BUS to 0.6f,
                LocalVehicle.TRUCK to 0.7f,
                LocalVehicle.TRACTOR to 0.3f,
                LocalVehicle.BICYCLE to 0.6f
            ),
            commonHazards = listOf(
                HazardKind.CATTLE, HazardKind.STRAY_DOG, HazardKind.SPEED_BREAKER,
                HazardKind.POTHOLE, HazardKind.ROADSIDE_VENDOR, HazardKind.CONSTRUCTION_CONE,
                HazardKind.PARKED_VEHICLE
            ),
            pedestrianUnpredictability = 0.75f
        )

        val USA = CountryProfile(
            "US", "United States", DrivingSide.RIGHT, SpeedUnit.MPH, false,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.TRUCK to 0.9f, LocalVehicle.BUS to 0.4f,
                LocalVehicle.MOTORCYCLE to 0.5f, LocalVehicle.BICYCLE to 0.4f),
            listOf(HazardKind.DEER, HazardKind.BROKEN_DOWN_VEHICLE, HazardKind.CONSTRUCTION_CONE),
            0.30f
        )
        val UK = CountryProfile(
            "GB", "United Kingdom", DrivingSide.LEFT, SpeedUnit.MPH, true,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.BUS to 0.6f, LocalVehicle.TRUCK to 0.7f,
                LocalVehicle.BICYCLE to 0.7f, LocalVehicle.MOTORCYCLE to 0.4f),
            listOf(HazardKind.BROKEN_DOWN_VEHICLE, HazardKind.CONSTRUCTION_CONE),
            0.30f
        )
        val GERMANY = CountryProfile(
            "DE", "Germany", DrivingSide.RIGHT, SpeedUnit.KMH, false,
            mapOf(LocalVehicle.CAR to 1.5f, LocalVehicle.TRUCK to 0.9f, LocalVehicle.MOTORCYCLE to 0.5f,
                LocalVehicle.BICYCLE to 0.6f),
            listOf(HazardKind.BROKEN_DOWN_VEHICLE, HazardKind.CONSTRUCTION_CONE),
            0.25f
        )
        val FRANCE = CountryProfile(
            "FR", "France", DrivingSide.RIGHT, SpeedUnit.KMH, false,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.SCOOTER to 0.7f, LocalVehicle.TRUCK to 0.7f,
                LocalVehicle.BICYCLE to 0.6f),
            listOf(HazardKind.CONSTRUCTION_CONE, HazardKind.BROKEN_DOWN_VEHICLE),
            0.35f
        )
        val JAPAN = CountryProfile(
            "JP", "Japan", DrivingSide.LEFT, SpeedUnit.KMH, true,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.SCOOTER to 0.8f, LocalVehicle.BICYCLE to 0.9f,
                LocalVehicle.BUS to 0.5f),
            listOf(HazardKind.CONSTRUCTION_CONE),
            0.20f
        )
        val SOUTH_KOREA = CountryProfile(
            "KR", "South Korea", DrivingSide.RIGHT, SpeedUnit.KMH, false,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.SCOOTER to 0.9f, LocalVehicle.BUS to 0.5f,
                LocalVehicle.TRUCK to 0.6f),
            listOf(HazardKind.CONSTRUCTION_CONE, HazardKind.PARKED_VEHICLE),
            0.35f
        )
        val AUSTRALIA = CountryProfile(
            "AU", "Australia", DrivingSide.LEFT, SpeedUnit.KMH, true,
            mapOf(LocalVehicle.CAR to 1.4f, LocalVehicle.TRUCK to 0.9f, LocalVehicle.MOTORCYCLE to 0.5f),
            listOf(HazardKind.DEER, HazardKind.BROKEN_DOWN_VEHICLE),
            0.30f
        )
        val UAE = CountryProfile(
            "AE", "United Arab Emirates", DrivingSide.RIGHT, SpeedUnit.KMH, false,
            mapOf(LocalVehicle.CAR to 1.6f, LocalVehicle.SUV to 1.0f, LocalVehicle.TRUCK to 0.6f),
            listOf(HazardKind.DUST_CLOUD, HazardKind.CONSTRUCTION_CONE),
            0.25f
        )
        val BRAZIL = CountryProfile(
            "BR", "Brazil", DrivingSide.RIGHT, SpeedUnit.KMH, false,
            mapOf(LocalVehicle.CAR to 1.3f, LocalVehicle.MOTORCYCLE to 1.1f, LocalVehicle.BUS to 0.7f,
                LocalVehicle.TRUCK to 0.7f),
            listOf(HazardKind.POTHOLE, HazardKind.SPEED_BREAKER, HazardKind.STRAY_DOG),
            0.55f
        )

        /** Registry (spec §4 initial support list). */
        val ALL: List<CountryProfile> = listOf(
            INDIA, USA, UK, GERMANY, FRANCE, JAPAN, SOUTH_KOREA, AUSTRALIA, UAE, BRAZIL
        )

        fun byCode(code: String): CountryProfile =
            ALL.firstOrNull { it.code == code } ?: INDIA
    }
}

/** Local vehicle flavours used for the country-specific traffic mix. */
enum class LocalVehicle {
    CAR, SUV, HATCHBACK, SEDAN, TRUCK, BUS, SCHOOL_BUS, VAN,
    MOTORCYCLE, SCOOTER, BICYCLE, AUTO_RICKSHAW, E_RICKSHAW, TRACTOR
}
