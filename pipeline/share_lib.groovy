@Library('share_lib') _

trivyDefectdojoPipeline(
    agentLabel: 'trivy',
    gitUrl: 'https://github.com/chengdevith/jobservice.git',
    gitBranch: 'main',
    imageName: 'jobservice',
    trivyPath: '/home/enz/trivy/docker-compose.yml',
    defectdojoUrl: 'https://defectdojo.devith.it.com',
    defectdojoCredentialId: 'DEFECTDOJO',
    productTypeName: 'Web Applications',
    productName: 'JobFinder',
    engagementName: 'Jenkins-CI',
    testTitle: 'Trivy Image Scan',
    gateSeverity: 'CRITICAL'
)
