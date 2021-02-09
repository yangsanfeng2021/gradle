package Gradle_Promotion.patches.buildTypes

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the buildType with uuid = '355487d7-45b9-4387-9fc5-713e7683e6d0' (id = 'Gradle_Promotion_StartReleaseCycle')
accordingly, and delete the patch script.
*/
changeBuildType(uuid("355487d7-45b9-4387-9fc5-713e7683e6d0")) {
    check(paused == false) {
        "Unexpected paused: '$paused'"
    }
    paused = true
}
