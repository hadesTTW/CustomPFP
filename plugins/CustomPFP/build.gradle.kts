version = "1.0.0"
description = "Set custom profile pictures for any user"

aliucord {
    changelog.set(
        """
        # 1.0.0
        * Initial release
        * /pfp set <user> <url> - Set custom profile picture
        * /pfp clear <user> - Remove custom profile picture
        """.trimIndent()
    )
    deploy.set(false)
}
