#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
    upstream('knime-base/' + env.BRANCH_NAME.replaceAll('/', '%2F'))]),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.javasnippet', 'maven && ui')

    workflowTests.runTests(
        dependencies: [
            repositories: [
                'knime-javasnippet', 'knime-virtual', 'knime-datageneration', 'knime-timeseries',
                'knime-jep', 'knime-filehandling', 'knime-xml', 'knime-streaming', 'knime-jfreechart',
                'knime-distance', 'knime-js-core', 'knime-js-base', 'knime-base'
            ]
        ]
    )

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}
/* vim: set shiftwidth=4 expandtab smarttab: */
