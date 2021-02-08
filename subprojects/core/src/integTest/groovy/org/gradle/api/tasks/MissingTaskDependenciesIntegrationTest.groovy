/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

@Unroll
class MissingTaskDependenciesIntegrationTest extends AbstractIntegrationSpec {

    def "detects missing dependency between two tasks (#description)"() {
        buildFile << """
            task producer {
                def outputFile = file("${producedLocation}")
                outputs.${outputType}(${producerOutput == null ? 'outputFile' : "'${producerOutput}'"})
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("${consumedLocation}")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        when:
        expectMissingDependencyDeprecation(":producer", ":consumer", file(consumedLocation))
        then:
        succeeds("producer", "consumer")

        when:
        expectMissingDependencyDeprecation(":producer", ":consumer", file(producerOutput ?: producedLocation))
        then:
        succeeds("consumer", "producer")

        where:
        description            | producerOutput | outputType | producedLocation           | consumedLocation
        "same location"        | null           | "file"     | "output.txt"               | "output.txt"
        "consuming ancestor"   | null           | "file"     | "build/dir/sub/output.txt" | "build/dir"
        "consuming descendant" | 'build/dir'    | "dir"      | "build/dir/sub/output.txt" | "build/dir/sub/output.txt"
    }

    def "ignores missing dependency if there is an #relation relation in the other direction"() {
        def sourceDir = "src"
        file(sourceDir).createDir()
        def outputDir = "build/output"

        buildFile << """
            task firstTask {
                inputs.dir("${sourceDir}")
                def outputDir = file("${outputDir}")
                outputs.dir(outputDir)
                doLast {
                    new File(outputDir, "source").text = "fixed"
                }
            }

            task secondTask {
                def inputDir = file("${outputDir}")
                def outputDir = file("${sourceDir}")
                inputs.dir(inputDir)
                outputs.dir(outputDir)
                doLast {
                    new File(outputDir, "source").text = "fixed"
                }
            }

            secondTask.${relation}(firstTask)
        """

        expect:
        succeeds("firstTask", "secondTask")
        succeeds("firstTask", "secondTask")

        where:
        relation << ['dependsOn', 'mustRunAfter']
    }

    def "does not detect missing dependency when consuming the sibling of the output of the producer"() {
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("build/notOutput.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        expect:
        succeeds("producer", "consumer")
        succeeds("consumer", "producer")
    }

    def "transitive dependencies are accepted as valid dependencies (including #dependency)"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            task a
            task b
            task c
            task d

            consumer.dependsOn(d)

            d.dependsOn(c)
            ${dependency}
            b.dependsOn(a)

            a.dependsOn(producer)
        """

        expect:
        // We add the intermediate tasks here, since the dependency relation doesn't necessarily force their scheduling
        succeeds("producer", "b", "c", "consumer")

        where:
        dependency            | _
        "c.dependsOn(b)"      | _
        "c.mustRunAfter(b)"   | _
        "b.finalizedBy(c)"    | _
    }

    def "only having shouldRunAfter causes a validation warning"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }

            consumer.shouldRunAfter(producer)
        """

        expect:
        expectMissingDependencyDeprecation(":producer", ":consumer", file("output.txt"))
        succeeds("producer", "consumer")
    }

    def "detects missing dependencies even if the consumer does not have outputs"() {
        buildFile << """
            task producer {
                def outputFile = file("output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("output.txt")
                inputs.files(inputFile)
                doLast {
                    println "Hello " + inputFile.text
                }
            }
        """

        expect:
        expectMissingDependencyDeprecation(":producer", ":consumer", file("output.txt"))
        succeeds("producer", "consumer")
    }

    def "takes filters for inputs into account when detecting missing dependencies"() {
        file("src/main/java/MyClass.java").createFile()
        buildFile << """
            task producer {
                def outputFile = file("build/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "first"
                }
            }
            task filteredConsumer(type: Zip) {
                from(project.projectDir) {
                    include 'src/**'
                }
                destinationDirectory = file("build")
                archiveBaseName = "output3"
            }
        """

        when:
        run("producer", "filteredConsumer")
        then:
        executedAndNotSkipped(":producer", ":filteredConsumer")
        when:
        run("filteredConsumer", "producer")
        then:
        skipped(":producer", ":filteredConsumer")
    }

    def "detects missing dependencies when using filtered inputs"() {
        file("src/main/java/MyClass.java").createFile()
        buildFile << """
            task producer {
                def outputFile = file("build/problematic/output.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "first"
                }
            }
            task consumer(type: Zip) {
                from(project.projectDir) {
                    include 'build/problematic/**'
                }
                destinationDirectory = file("build")
                archiveBaseName = "outputZip"
            }
        """

        when:
        expectMissingDependencyDeprecation(":producer", ":consumer", testDirectory)
        run("producer", "consumer")
        then:
        executedAndNotSkipped(":producer", ":consumer")

        when:
        expectMissingDependencyDeprecation(":producer", ":consumer", file("build/problematic/output.txt"))
        run("consumer", "producer")
        then:
        executed(":producer", ":consumer")
    }

    def "emits a deprecation warning when an input file collection can't be resolved"() {
        buildFile """
            task "broken" {
                inputs.files(5).withPropertyName("invalidInputFileCollection")

                doLast {
                    println "success"
                }
            }
        """
        when:
        executer.expectDocumentedDeprecationWarning("Consider using Task.dependsOn instead of an input file collection. This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. Execution optimizations are disabled due to the failed validation. See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")
        run "broken"
        then:
        executedAndNotSkipped ":broken"
        outputContains("""
            Validation failed for task ':broken', disabling optimizations:
              - Property 'invalidInputFileCollection' cannot be resolved:
              Cannot convert the provided notation to a File or URI: 5.
              The following types/formats are supported:
                - A String or CharSequence path, for example 'src/main/java' or '/usr/include'.
                - A String or CharSequence URI, for example 'file:/usr/include'.
                - A File instance.
                - A Path instance.
                - A Directory instance.
                - A RegularFile instance.
                - A URI or URL instance.
                - A TextResource instance.
            Consider using Task.dependsOn instead of an input file collection.""".stripIndent())
    }

    void expectMissingDependencyDeprecation(String producer, String consumer, File producedConsumedLocation) {
        executer.expectDocumentedDeprecationWarning(
            "Task '${consumer}' uses the output of task '${producer}', without declaring an explicit dependency (using Task.dependsOn() or Task.mustRunAfter()) or an implicit dependency (declaring task '${producer}' as an input). " +
                "The location which is an input/output is '${producedConsumedLocation.absolutePath}'. " +
                "This can lead to incorrect results being produced, depending on what order the tasks are executed. " +
                "This behaviour has been deprecated and is scheduled to be removed in Gradle 7.0. " +
                "Execution optimizations are disabled due to the failed validation. " +
                "See https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:up_to_date_checks for more details.")
    }
}
