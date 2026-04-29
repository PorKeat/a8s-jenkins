pipeline {
    agent { label 'trivy' }

    environment {
        IMAGE_NAME = "jobservice"
        IMAGE_TAG  = "${BUILD_NUMBER}"
        FULL_IMAGE = "${IMAGE_NAME}:${IMAGE_TAG}"
        TRIVY_PATH = "/home/enz/trivy/docker-compose.yml"
        DEFECTDOJO_URL = "https://defectdojo.devith.it.com"
        DEFECTDOJO_API_KEY = credentials('DEFECTDOJO')
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/chengdevith/jobservice.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'ls -la'
                sh 'docker build -t ${FULL_IMAGE} .'
            }
        }

        stage('Trivy Scan') {
            steps {
                sh '''
                mkdir -p /home/enz/trivy/reports
                docker compose -f ${TRIVY_PATH} exec -T trivy \
                trivy image \
                --scanners vuln,secret,misconfig \
                --format json \
                -o /reports/trivy-report.json \
                ${FULL_IMAGE}
                '''
            }
        }

        stage('Security Gate') {
            steps {
                sh '''
                docker compose -f ${TRIVY_PATH} exec -T trivy \
                trivy image \
                --severity CRITICAL \
                --exit-code 1 \
                --no-progress \
                ${FULL_IMAGE}
                '''
            }
        }

        stage('Upload to DefectDojo') {
            steps {
                sh '''
                curl --fail -k -X POST "${DEFECTDOJO_URL}/api/v2/reimport-scan/" \
                -H "Authorization: Token ${DEFECTDOJO_API_KEY}" \
                -F "scan_type=Trivy Scan" \
                -F "file=@/home/enz/trivy/reports/trivy-report.json" \
                -F "auto_create_context=true" \
                -F "product_type_name=Web Applications" \
                -F "product_name=JobFinder" \
                -F "engagement_name=Jenkins-CI" \
                -F "test_title=Trivy Image Scan" \
                -F "active=true" \
                -F "verified=true" \
                -F "close_old_findings=true"
                '''
            }
        }

        stage('Fail on Critical') {
            steps {
                sh '''
                docker compose -f ${TRIVY_PATH} exec -T trivy \
                  trivy image \
                  --severity CRITICAL \
                  --exit-code 1 \
                  --no-progress \
                  ${FULL_IMAGE}
                '''
            }
        }
    }

    post {
        always {
            sh 'cp /home/enz/trivy/reports/trivy-report.json ./trivy-report.json || true'
            archiveArtifacts artifacts: 'trivy-report.json', fingerprint: true, allowEmptyArchive: true
        }
    }
}
