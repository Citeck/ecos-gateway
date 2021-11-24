properties([
    buildDiscarder(logRotator(daysToKeepStr: '', numToKeepStr: '3')),
])
timestamps {
  node {

    def repoUrl = "git@bitbucket.org:citeck/ecos-gateway.git"

    stage('Checkout Script Tools SCM') {
      dir('jenkins-script-tools') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "script-tools"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [],
          submoduleCfg: [],
          userRemoteConfigs: [[credentialsId: 'awx.integrations', url: 'git@bitbucket.org:citeck/pipelines.git']]
        ])
      }
    }
    currentBuild.changeSets.clear()
    def buildTools = load "jenkins-script-tools/scripts/build-tools.groovy"

    try {
      stage('Checkout SCM') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "${env.BRANCH_NAME}"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [],
          submoduleCfg: [],
          userRemoteConfigs: [[credentialsId: 'awx.integrations', url: repoUrl]]
        ])
      }
      def project_version = readMavenPom().getVersion().toLowerCase()
      if ((env.BRANCH_NAME != "master") && (!project_version.contains('snapshot')))  {
        echo "Assembly of release artifacts is allowed only from the master branch!"
        //currentBuild.result = 'SUCCESS'
        //return
      }
      buildTools.notifyBuildStarted(repoUrl, project_version, env)
      stage('Build project artifacts') {
        withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
          sh "mvn clean package -Dskip.npm -Pprod,logToFile"
          // build-info
          def buildData = buildTools.getBuildInfo(repoUrl, "${env.BRANCH_NAME}", project_version)
          dir('target/classes/build-info') {
              buildTools.writeBuildInfoToFiles(buildData)
          }
          // /build-info
          sh "mvn jib:dockerBuild -Dskip.npm -Pprod,logToFile -Djib.docker.image.tag=${project_version}"
        }
        junit '**/target/surefire-reports/*.xml'
      }
      stage('Push docker image') {
        docker.withRegistry('http://127.0.0.1:8082', '7d800357-2193-4474-b768-5c27b97a1030') {
          def microserviceImage = "ecos-gateway"+":"+"${project_version}"
          def current_microserviceImage = docker.image("${microserviceImage}")
          current_microserviceImage.push()
        }
      }
    } catch (Exception e) {
      currentBuild.result = 'FAILURE'
      error_message = e.getMessage()
      echo error_message
    }
    script {
      if (currentBuild.result != 'FAILURE') {
        buildTools.notifyBuildSuccess(repoUrl, env)
      } else {
        buildTools.notifyBuildFailed(repoUrl, error_message, env)
      }
    }
  }
}
