# Jenkins Project Setup Notes

Sensitive document. This file contains live credentials and private key material.

## Jenkins Access

- URL: `https://jenkins.autonomous-istad.com/`
- Login username: `replace-me`
- Login password: `replace-me`

## Main Jobs

- `a8s-admin`
- `a8s-backend`
- `a8s-frontend`
- `deploy-microservices`
- `deploy-pipeline`
- `deploy-pipeliness`
- `deploy-service-test`
- `share_lib`
- `trivy`

## Agents / Nodes

### Built-In Node

- Type: Jenkins controller built-in node
- Executors: `2`

### Trivy Node

- Label: `trivy`
- Executors: `1`
- Remote path: `/home/enz/jenkins`
- Used by the image build/scan/push stage in the shared deploy pipeline

## Project Pipeline Wiring

### a8s-backend

- Repository: `https://github.com/ITProfessional-Gen01/a8s-backend.git`
- Branch: `main`
- Pipeline source: `Jenkinsfile`
- SCM credential ID: `a8s-backend-credential`
- Trigger: GitHub push

### a8s-frontend

- Repository: `https://github.com/ITProfessional-Gen01/a8s-frontend.git`
- Branch: `main`
- Pipeline source: `Jenkinsfile`
- SCM credential ID: `a8s-frontend-credential`
- Trigger: GitHub push

### a8s-admin

- Repository: `https://github.com/ITProfessional-Gen01/a8s-admin.git`
- Branch: `main`
- Pipeline source: `Jenkinsfile`
- SCM credential ID: `a8s-backend-credential`
- Trigger: GitHub push

## Shared Deploy Pipeline

Job: `deploy-pipeline`

### Shared Library

- Library usage: `@Library('share_lib@master') _`

### Key Parameters

- `REPO_URL`
- `BRANCH`
- `USER_ID`
- `WORKSPACE_ID`
- `CUSTOM_DOMAIN`
- `PROJECT_NAME`
- `APP_NAME`
- `APP_PORT`
- `PLATFORM_DOMAIN`
- `GITOPS_BRANCH`
- `REPO_CREDENTIALS_ID`
- `ENABLE_TRIVY_SCAN`
- `ENABLE_GITOPS_UPDATE`
- `DOMAIN_ONLY_UPDATE`

### Main Flow

1. Validate input
2. Checkout infra repository
3. Checkout user repository
4. Detect framework
5. Prepare or generate Dockerfile
6. Build image
7. Run Trivy scan on the `trivy` agent
8. Upload scan to DefectDojo
9. Push image to registry
10. Update GitOps repository

### Infra Scripts Used

- `detect-framework.sh`
- `generate-dockerfile.sh`
- `update-gitops.sh`

### Chart Source Used By Deploy Pipeline

- `helm/app-template`

## Important Jenkins Credentials

Only credential metadata and known user-provided values are documented here.

### 1. SonarQube Token

- Type: `Secret text`
- ID: `sonarqube-token`
- Description: `sonarqube-token`
- Token:

```text
replace-me
```

### 2. Monolithic GitOps SSH Credential

- Type: `SSH Username with private key`
- ID: `gitops-ssh`
- Username: `git`
- Private key:

```text
[redacted - store the real key only in config-secret.yml]
```

## Other Visible Credential IDs Referenced In Jenkins

- `TRIVY`
- `DEFECTDOJO`
- `infra-repo-url`
- `registry-url`
- `registry-credentials`
- `infra-repo-creds`
- `registry-repository`
- `gitops-repo-url`
- `infra-repo-url-micro`
- `registry-repository-micro`
- `gitops-repo-url-micro`
- `dockerhub-credentials-micro`
- `gitops-micro-creds`
- `DOCKERHUB-CREDENTIAL`
- `a8s-frontend-credential`
- `a8s-frontend-chart-credential`
- `a8s-frontend-env`
- `register-url-micro`
- `a8s-backend-chart-credential`
- `a8s-backend-credential`
- `a8s-admin-registry-repository`
- `a8s-admin-registry-credentials`
- `a8s-admin-github-token`
- `sonarqube-token`

## Plugins Confirmed

### SonarQube

- `sonar`
  - Long name: `SonarQube Scanner for Jenkins`
  - Version: `2.18.2`
- `sonar-quality-gates`
  - Long name: `Sonar Quality Gates Plugin`
  - Version: `364.v67a_f255f340f`

### Pipeline / SCM / Credentials Highlights

- `workflow-aggregator`
- `workflow-cps`
- `workflow-job`
- `pipeline-model-definition`
- `pipeline-groovy-lib`
- `git`
- `git-client`
- `github`
- `github-api`
- `github-branch-source`
- `credentials`
- `credentials-binding`
- `ssh-credentials`
- `ssh-slaves`
- `ws-cleanup`
- `timestamper`
- `gradle`
- `email-ext`

## Shared Library Example

The `share_lib` job currently runs:

- Library: `@Library('share_lib') _`
- Function: `trivyDefectdojoPipeline(...)`
- Agent label: `trivy`
- Git URL: `https://github.com/chengdevith/jobservice.git`
- Branch: `main`
- Image name: `jobservice`
- Trivy path: `/home/enz/trivy/docker-compose.yml`
- DefectDojo URL: `https://defectdojo.devith.it.com`
- DefectDojo credential ID: `DEFECTDOJO`
- Severity gate: `CRITICAL`

## Recommended Security Follow-Up

Because this document contains a token and a private key:

1. Store it only in a secure vault or private encrypted note system.
2. Do not commit it into Git.
3. Rotate the SonarQube token and GitOps SSH key if they were shared beyond the intended team.
