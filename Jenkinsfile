pipeline {
    agent any
    stages {
        stage('Build') {
          steps {
            sh "mvn clean package -P!webpack -DskipTests=true -Djib.docker.image.tag=develop jib:dockerBuild"
          }
        }
        stage('Push docker images') {
          steps {
            withCredentials([usernamePassword(credentialsId: '3400f5ec-0ef3-4944-b59a-97e67680777a', passwordVariable: 'pass', usernameVariable: 'user')]) {
              sh "docker login -u $user -p $pass nexus.citeck.ru"
              sh "docker push nexus.citeck.ru/gateway:develop"
            }
          }
        }
    }
}