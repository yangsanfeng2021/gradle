/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package promotion

import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule

class PublishNightlySnapshot(branch: VersionedSettingsBranch) : PublishGradleDistribution(
    versionSettingsBranch = branch,
    promotedBranch = branch.branchName,
    task = branch.promoteNightlyTaskName(),
    triggerName = "ReadyforNightly"
) {
    init {
        id("Promotion_${branch.asBuildTypeId()}Nightly")
        name = "Nightly Snapshot"
        description = "Promotes the latest successful changes on '${branch.branchName}' from Ready for Nightly as a new nightly snapshot"

        triggers {
            schedule {
                schedulingPolicy = daily {
                    this.hour = branch.triggeredHour()
                }
                triggerBuild = always()
                withPendingChangesOnly = false
            }
        }
    }
}

// Avoid two jobs running at the same time and causing troubles
private fun VersionedSettingsBranch.triggeredHour() = when (this.branchName) {
    "master" -> 0
    "release" -> 1
    else -> 0
}
