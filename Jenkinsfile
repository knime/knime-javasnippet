#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream('knime-base/' + env.BRANCH_NAME.replaceAll('/', '%2F'))
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.javasnippet', 'maven && ui')

    workflowTests.runTests(
        dependencies: [
            repositories: [
                'knime-core',
                'knime-base',
                'knime-datageneration',
                'knime-distance',
                'knime-filehandling',
                'knime-javasnippet',
                'knime-jep',
                'knime-jfreechart',
                'knime-js-base',
                'knime-js-core',
                'knime-streaming',
                'knime-timeseries',
                'knime-virtual',
                'knime-xml'
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
