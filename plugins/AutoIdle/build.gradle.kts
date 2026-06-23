version = "1.1.0" // Plugin version. Increment this to trigger an update
description = "Marks you idle when unfocusing Aliucord, restores your status on focus like RN does (only if you were online before)"

aliucord {
    changelog.set(
        """
        # 1.1.0
        * fix keeping your status when autoidling
        * reduce qty of false positives
        # 1.0.0
        * Initial plugin release!
        """.trimIndent(),
    )

    deploy.set(true)
}
