//test
properties([
    buildDiscarder(logRotator(daysToKeepStr: '3', numToKeepStr: '3')),
])
timestamps {
  node {
    mattermostSend endpoint: 'https://mm.citeck.ru/hooks/9ytch3uox3retkfypuq7xi3yyr', channel: "build_notifications", color: 'good', message: " :arrow_forward: Build info - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
    try {
      stage('Checkout SCM') {
        checkout([
          $class: 'GitSCM',
          branches: [[name: "${env.BRANCH_NAME}"]],
          doGenerateSubmoduleConfigurations: false,
          extensions: [],
          submoduleCfg: [],
          userRemoteConfigs: [[credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66',url: 'git@bitbucket.org:citeck/ecos-gateway.git']]
        ])
      }
      def project_version = readMavenPom().getVersion().toLowerCase()
      if ((env.BRANCH_NAME != "master") && (!project_version.contains('snapshot')))  {
        echo "Assembly of release artifacts is allowed only from the master branch!"
        currentBuild.result = 'SUCCESS'
        return
      }
      stage('Build project artifacts') {
        withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
          sh "mvn clean package -Pprod -Djib.docker.image.tag=${project_version} jib:dockerBuild"
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
    }
    catch (Exception e) {
      currentBuild.result = 'FAILURE'
      error_message = e.getMessage()
      echo error_message
    }
    script{
      if(currentBuild.result != 'FAILURE'){
        mattermostSend endpoint: 'https://mm.citeck.ru/hooks/9ytch3uox3retkfypuq7xi3yyr', channel: "build_notifications", color: 'good', message: " :white_check_mark: Build complete - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>)"
      }
      else{
        mattermostSend endpoint: 'https://mm.citeck.ru/hooks/9ytch3uox3retkfypuq7xi3yyr', channel: "build_notifications", color: 'danger', message: " @channel :exclamation: Build failure - ${env.JOB_NAME} ${env.BUILD_NUMBER} (<${env.BUILD_URL}|Open>) :\n${error_message}"
      }
    }
  }
}
