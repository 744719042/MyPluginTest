package com.example.upload

import org.gradle.api.Plugin
import org.gradle.api.Project

class UploadArchive implements Plugin<Project> {
    void apply(Project project) {
        project.extensions.create("archive", ArchiveInfo)
        project.task("makeAar", type: MakeAarTask)

        project.apply plugin: 'maven'
        project.uploadArchives {
            repositories {
                mavenDeployer {
                    pom.project {
                        groupId 'com.example'
                        artifactId project.name
                        version project.rootProject.ext.aarVersion
                        packaging 'aar'

                        licenses {
                            license {
                                name 'The Apache Software License, Version 2.0'
                                url 'http://www.apache.org/licenses/LICENSE-2.0.txt'
                                comments 'A business-friendly OSS license'
                            }
                        }

                        developers {
                            developer {
                                id 'admin'
                                name 'admin'
                                email 'zxz@meitu.com'
                            }
                        }
                    }
                    repository(url: "http://127.0.0.1:8888/nexus/content/repositories/releases") {
                        authentication(userName: "admin", password: "admin123")
                    }
                    snapshotRepository(url: "http://127.0.0.1:8888/nexus/content/repositories/snapshot") {
                        authentication(userName: "admin", password: "admin123")
                    }
                }
            }
        }

        project.task("uploadAar")

        project.afterEvaluate {
            String buildType = project.archive.buildType == 'Debug' ? 'Debug' : 'Release'
            def makeAar = project.tasks.findByName("makeAar")
            def assemble = project.tasks.findByName("assemble$buildType")
            makeAar.dependsOn assemble

            def uploadAar = project.tasks.findByName("uploadAar")
            def uploadArchives = project.tasks.findByName("uploadArchives")
            uploadAar.dependsOn uploadArchives
        }

        project.task("cleanAars", type: CleanAarTask)
        project.task("makeAars", type: MakeAllAarsTask)
    }
}
