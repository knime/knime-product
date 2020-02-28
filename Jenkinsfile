#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
	// provide a list of upstream jobs which should trigger a rebuild of this job
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
	// provide the name of the update site project
	knimetools.defaultTychoBuild('org.knime.update.product')

 } catch (ex) {
	 currentBuild.result = 'FAILED'
	 throw ex
 } finally {
	 notifications.notifyBuild(currentBuild.result);
 }

/* vim: set ts=4: */
