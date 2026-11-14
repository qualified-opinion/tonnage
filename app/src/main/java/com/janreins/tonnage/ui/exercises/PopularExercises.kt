package com.janreins.tonnage.ui.exercises

/**
 * The staple gym lifts, most common first, so the exercise list can show them above the
 * rest of the 800-odd library exercises. Values are free-exercise-db ids; a test checks
 * every one exists in `exercises.json`. Nothing is stored: this is display order only.
 */
object PopularExercises {
    val ids: List<String> = listOf(
        // The big ones first: these fill the Popular section when nothing is filtered.
        "Barbell_Bench_Press_-_Medium_Grip",
        "Barbell_Squat",
        "Barbell_Deadlift",
        "Standing_Military_Press",
        "Bent_Over_Barbell_Row",
        "Pullups",
        "Wide-Grip_Lat_Pulldown",
        "Dumbbell_Bench_Press",
        "Incline_Dumbbell_Press",
        "Romanian_Deadlift",
        "Leg_Press",
        "Dumbbell_Shoulder_Press",
        // Then the usual accessories, roughly by how often people do them.
        "Seated_Cable_Rows",
        "Barbell_Curl",
        "Triceps_Pushdown",
        "Side_Lateral_Raise",
        "Barbell_Incline_Bench_Press_-_Medium_Grip",
        "One-Arm_Dumbbell_Row",
        "Chin-Up",
        "Leg_Extensions",
        "Lying_Leg_Curls",
        "Seated_Leg_Curl",
        "Barbell_Hip_Thrust",
        "Dumbbell_Lunges",
        "Split_Squat_with_Dumbbells",
        "Front_Barbell_Squat",
        "Hack_Squat",
        "Goblet_Squat",
        "Sumo_Deadlift",
        "Standing_Calf_Raises",
        "Seated_Calf_Raise",
        "Dumbbell_Bicep_Curl",
        "Hammer_Curls",
        "EZ-Bar_Curl",
        "Preacher_Curl",
        "Triceps_Pushdown_-_Rope_Attachment",
        "EZ-Bar_Skullcrusher",
        "Cable_Rope_Overhead_Triceps_Extension",
        "Close-Grip_Barbell_Bench_Press",
        "Dips_-_Triceps_Version",
        "Dips_-_Chest_Version",
        "Pushups",
        "Butterfly",
        "Cable_Crossover",
        "Dumbbell_Flyes",
        "Machine_Bench_Press",
        "Face_Pull",
        "Reverse_Flyes",
        "Arnold_Dumbbell_Press",
        "Barbell_Shrug",
        "Dumbbell_Shrug",
        "Hyperextensions_Back_Extensions",
        "Hanging_Leg_Raise",
        "Cable_Crunch",
        "Plank",
        "Crunches",
    )
}
