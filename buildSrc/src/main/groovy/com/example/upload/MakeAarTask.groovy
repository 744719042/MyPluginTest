package com.example.upload

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * 负责将当前Project的aar生成并拷贝到aars目录
 */
class MakeAarTask extends DefaultTask {

    @TaskAction
    void makeAar() {
        def aarDir = new File(project.getRootProject().getRootDir(), "aars")
        if (!aarDir.exists()) {
            aarDir.mkdir()
        }

        String buildType = project.archive.buildType == 'Debug' ? 'Debug' : 'Release'
        String aarName = project.archive.aarName
        if (aarName == null || aarName == '') {
            aarName = project.getName() + buildType + "_" + System.currentTimeMillis() + ".aar"
        }
        project.copy {
            from project.getBuildDir().getAbsolutePath() + "/outputs/aar/" + project.getName() + "-" + buildType.toLowerCase() + ".aar"
            into aarDir
            rename {
                String fileName -> aarName
            }
        }
    }
}