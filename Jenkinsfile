pipeline {
    agent any

    // Maven installation registered in "Manage Jenkins > Tools"
    tools {
        maven 'Maven3'
    }

    environment {
        // Let the Jenkins service find docker and mvn on macOS
        PATH = "/usr/local/bin:/opt/homebrew/bin:${PATH}"
        // Docker Hub namespace/repository for the image
        IMAGE = "jnmarenco/otp2-temperature-fx"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit tests') {
            steps {
                sh 'mvn -B test'
            }
        }

        stage('Code coverage (JaCoCo)') {
            steps {
                sh 'mvn -B jacoco:report'
            }
        }

        stage('Publish test results') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }

        stage('Publish coverage report') {
            steps {
                jacoco()
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
            }
        }

        stage('Docker build') {
            steps {
                sh 'docker build -t $IMAGE:$BUILD_NUMBER -t $IMAGE:latest .'
            }
        }

        stage('Docker push') {
            steps {
                withCredentials([usernamePassword(
                        credentialsId: 'dockerhub',
                        usernameVariable: 'DH_USER',
                        passwordVariable: 'DH_PASS')]) {
                    sh 'echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin'
                    sh 'docker push $IMAGE:$BUILD_NUMBER'
                    sh 'docker push $IMAGE:latest'
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
            sh 'docker logout || true'
        }
    }
}
