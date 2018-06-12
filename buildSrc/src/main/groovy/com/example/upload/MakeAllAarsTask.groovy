package com.example.upload

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 * 将所有子项目的aar生成并拷贝到aars目录
 */
class MakeAllAarsTask extends DefaultTask {

    @TaskAction
    void makeAll() {
        Set<Project> subProjects = project.getSubprojects()
        subProjects.forEach {
            Project pro ->
                def makeAar = pro.tasks.findByName('makeAar')
                makeAar.execute()
        }
    }
}
