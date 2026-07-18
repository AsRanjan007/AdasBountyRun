package com.adas.bountyrun.config

/** Selectable road environments (spec §5). Not all are enabled in the MVP. */
enum class EnvironmentType(val displayName: String, val mvp: Boolean) {
    HIGHWAY("Highway", true),
    CITY("City", true),
    COUNTRYSIDE("Countryside", false),
    RACING_TRACK("Racing Track", false),
    DUSTY_ROAD("Dusty Road", false),
    MOUNTAIN_ROAD("Mountain Road", false),
    COASTAL_ROAD("Sea & Riverside Road", false),
    FLYOVER_TOLL("Flyover & Toll Road", false),
    NIGHT_ROAD("Night Road", false)
}

/** Weather conditions (spec §14). Each carries physics/visibility modifiers. */
enum class Weather(
    val displayName: String,
    /** Tyre grip multiplier (1 = dry). */
    val gripFactor: Float,
    /** Visibility 0..1 (1 = perfectly clear). */
    val visibility: Float,
    /** Sensor performance multiplier 0..1. */
    val sensorFactor: Float
) {
    CLEAR("Clear", 1.0f, 1.0f, 1.0f),
    RAIN("Rain", 0.8f, 0.8f, 0.85f),
    HEAVY_RAIN("Heavy Rain", 0.6f, 0.55f, 0.65f),
    FOG("Fog", 0.9f, 0.4f, 0.5f),
    DUST_STORM("Dust Storm", 0.7f, 0.35f, 0.45f),
    SNOW("Snow", 0.5f, 0.6f, 0.7f),
    THUNDERSTORM("Thunderstorm", 0.65f, 0.5f, 0.6f),
    STRONG_WIND("Strong Wind", 0.85f, 0.85f, 0.9f),
    WET_ROAD("Wet Road", 0.75f, 0.9f, 0.95f),
    FLOODED("Flooded Road", 0.55f, 0.7f, 0.8f)
}

/** Time of day (spec §14). Night reduces visibility and raises risk. */
enum class TimeOfDay(val displayName: String, val ambientLight: Float) {
    SUNRISE("Sunrise", 0.6f),
    MORNING("Morning", 0.95f),
    AFTERNOON("Afternoon", 1.0f),
    SUNSET("Sunset", 0.6f),
    NIGHT("Night", 0.28f),
    MIDNIGHT("Midnight", 0.18f);

    val isDark: Boolean get() = ambientLight < 0.45f
}

/** Game modes (spec §3). */
enum class GameMode(val displayName: String) {
    ADAS_AWARENESS("ADAS Awareness"),
    REALISTIC("Realistic Simulation"),
    BOUNTY_CHALLENGE("Bounty Challenge"),
    POLICE_ESCAPE("Police Escape"),
    ADAS_TRAINING("ADAS Training"),
    FREE_DRIVE("Free Drive")
}

/** Road hazards & obstacles (spec §6). */
enum class HazardKind(val displayName: String, val lethal: Boolean) {
    // Living hazards (casualty-capable)
    PEDESTRIAN("Pedestrian", true),
    CHILD("Child", true),
    ELDERLY("Elderly pedestrian", true),
    CYCLIST("Cyclist", true),
    MOTORCYCLIST("Motorcyclist", true),
    CATTLE("Cattle", true),
    STRAY_DOG("Stray dog", true),
    DEER("Deer", true),
    GOAT("Goat", true),
    // Object hazards
    FALLEN_CARGO("Fallen cargo", false),
    TYRE("Loose tyre", false),
    ROCK("Rock", false),
    POTHOLE("Pothole", false),
    WATERLOGGING("Waterlogging", false),
    OIL_SPILL("Oil spill", false),
    ROAD_BARRIER("Road barrier", false),
    CONSTRUCTION_CONE("Construction cone", false),
    BROKEN_DOWN_VEHICLE("Broken-down vehicle", false),
    OPEN_MANHOLE("Open manhole", false),
    TREE_BRANCH("Tree branch", false),
    LANDSLIDE("Landslide", false),
    SPEED_BREAKER("Speed breaker", false),
    PARKED_VEHICLE("Incorrectly parked vehicle", false),
    ROADSIDE_VENDOR("Roadside vendor", true),
    DUST_CLOUD("Dust cloud", false)
}
