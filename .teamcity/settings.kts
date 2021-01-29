import model.JsonBasedGradleSubprojectProvider
import model.StatisticBasedFunctionalTestBucketProvider
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import common.Branch
import projects.RootProject
import java.io.File

version = "2020.2"
val model = CIBuildModel(
    branch = Branch.current(),
    buildScanTags = listOf("Check"),
    subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json"))
)
DslContext.parentProjectId = AbsoluteId(model.rootProjectId)
DslContext.projectId = AbsoluteId(model.projectId)
DslContext.projectName = model.projectName
val gradleBuildBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, File("./test-class-data.json"))
project(RootProject(model, gradleBuildBucketProvider))
