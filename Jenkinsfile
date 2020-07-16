#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
    upstream('knime-tp/' + env.BRANCH_NAME.replaceAll('/', '%2F')),
        upstream(upstreamProjects: 'knime-core/' + env.BRANCH_NAME.replaceAll('/', '%2F'), threshold: 'UNSTABLE'),
        upstream(upstreamProjects: 'knime-base/' + env.BRANCH_NAME.replaceAll('/', '%2F'), threshold: 'UNSTABLE'),
        upstream(upstreamProjects: 'knime-workbench/' + env.BRANCH_NAME.replaceAll('/', '%2F'), threshold: 'UNSTABLE')
    ]),

    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.product')

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar([])
    }
} catch (ex) {
    currentBuild.result = 'FAILURE'
    throw ex
} finally {
    notifications.notifyBuild(currentBuild.result);
}

