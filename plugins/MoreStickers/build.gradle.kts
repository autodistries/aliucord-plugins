version = "0.1.0"
description = "Adds a sticker picker for imported sticker packs"

aliucord {
    changelog.set(
        """
        # 0.1.0
        * Initial release
        """.trimIndent(),
    )

    deploy.set(false)
}

// Avoid bundling the Kotlin stdlib into the plugin jar. The project-level
// `gradle.properties` already sets `kotlin.stdlib.default.dependency=false`.
// Add a compileOnly dependency so the stdlib is available at compile time but
// not packaged with the plugin (host provides the runtime).
dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib")
}
