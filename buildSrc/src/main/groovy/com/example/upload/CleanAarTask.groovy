package com.example.upload

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * 负责清空和删除aars目录
 */
class CleanAarTask extends DefaultTask {
    @TaskAction
    void clean() {
        def aarDir = new File(project.getRootProject().getRootDir(), "aars")
        for (file in aarDir.listFiles()) {
            if (file.isFile()) {
                file.delete()
            }
        }
        aarDir.delete()
    }
}