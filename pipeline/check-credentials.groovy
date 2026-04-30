pipeline {
    agent any

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Check Jenkins Credentials') {
            steps {
                script {
                    def stringCredentialIds = [
                        'sonarqube-token',
                        'DEFECTDOJO',
                        'registry-url',
                        'registry-repository',
                        'gitops-repo-url',
                        'infra-repo-url',
                        'infra-repo-url-micro',
                        'registry-repository-micro',
                        'gitops-repo-url-micro',
                        'register-url-micro',
                        'a8s-admin-registry-repository',
                        'a8s-admin-github-token',
                        'a8s-frontend-chart-credential',
                        'a8s-backend-chart-credential'
                    ]

                    def usernamePasswordCredentialIds = [
                        'a8s-backend-credential',
                        'a8s-frontend-credential',
                        'registry-credentials',
                        'DOCKERHUB-CREDENTIAL',
                        'infra-repo-creds',
                        'dockerhub-credentials-micro',
                        'gitops-micro-creds',
                        'a8s-admin-registry-credentials'
                    ]

                    def fileCredentialIds = [
                        'a8s-frontend-env'
                    ]

                    def sshCredentialIds = [
                        'gitops-ssh',
                        'trivy'
                    ]

                    def issueCount = 0
                    def results = []

                    def maskValue = { String value ->
                        if (!value) {
                            return '(empty)'
                        }
                        if (value.length() <= 8) {
                            return value[0] + ('*' * Math.max(value.length() - 2, 0)) + value[-1]
                        }
                        return value.take(4) + '...' + value.takeRight(4)
                    }

                    def record = { String id, String type, String status, String detail ->
                        results << [id: id, type: type, status: status, detail: detail]
                        if (status != 'HAVE') {
                            issueCount++
                        }
                    }

                    for (String id : stringCredentialIds) {
                        try {
                            withCredentials([string(credentialsId: id, variable: 'CHECK_SECRET')]) {
                                if (!env.CHECK_SECRET?.trim()) {
                                    record(id, 'string', 'EMPTY', '(empty value)')
                                } else if (env.CHECK_SECRET == 'replace-me') {
                                    record(id, 'string', 'PLACEHOLDER', 'replace-me')
                                } else {
                                    record(id, 'string', 'HAVE', maskValue(env.CHECK_SECRET))
                                }
                            }
                        } catch (Exception ignored) {
                            record(id, 'string', 'MISSING', '(not found)')
                        }
                    }

                    for (String id : usernamePasswordCredentialIds) {
                        try {
                            withCredentials([usernamePassword(
                                credentialsId: id,
                                usernameVariable: 'CHECK_USER',
                                passwordVariable: 'CHECK_PASS'
                            )]) {
                                if (!env.CHECK_USER?.trim() || !env.CHECK_PASS?.trim()) {
                                    record(id, 'username/password', 'EMPTY', "username=${env.CHECK_USER ?: '(empty)'} password=(empty)")
                                } else if (env.CHECK_USER == 'replace-me' || env.CHECK_PASS == 'replace-me') {
                                    record(id, 'username/password', 'PLACEHOLDER', "username=${env.CHECK_USER} password=${maskValue(env.CHECK_PASS)}")
                                } else {
                                    record(id, 'username/password', 'HAVE', "username=${env.CHECK_USER} password=${maskValue(env.CHECK_PASS)}")
                                }
                            }
                        } catch (Exception ignored) {
                            record(id, 'username/password', 'MISSING', '(not found)')
                        }
                    }

                    for (String id : fileCredentialIds) {
                        try {
                            withCredentials([file(credentialsId: id, variable: 'CHECK_FILE')]) {
                                def content = sh(script: 'cat "$CHECK_FILE"', returnStdout: true).trim()
                                if (!content) {
                                    record(id, 'file', 'EMPTY', '(empty file)')
                                } else if (content == 'replace-me') {
                                    record(id, 'file', 'PLACEHOLDER', 'replace-me')
                                } else {
                                    def lines = content.readLines().findAll { it?.trim() }
                                    def keys = lines.collect { line ->
                                        line.contains('=') ? line.substring(0, line.indexOf('=')) : line
                                    }.take(5)
                                    record(id, 'file', 'HAVE', "lines=${lines.size()} keys=${keys.join(', ')}")
                                }
                            }
                        } catch (Exception ignored) {
                            record(id, 'file', 'MISSING', '(not found)')
                        }
                    }

                    for (String id : sshCredentialIds) {
                        try {
                            withCredentials([sshUserPrivateKey(
                                credentialsId: id,
                                keyFileVariable: 'CHECK_KEY',
                                usernameVariable: 'CHECK_SSH_USER'
                            )]) {
                                def fileStatus = sh(
                                    script: 'if grep -q "replace-me" "$CHECK_KEY"; then echo PLACEHOLDER; else echo OK; fi',
                                    returnStdout: true
                                ).trim()
                                if (fileStatus == 'PLACEHOLDER' || env.CHECK_SSH_USER == 'replace-me') {
                                    record(id, 'ssh', 'PLACEHOLDER', "username=${env.CHECK_SSH_USER}")
                                } else {
                                    def fingerprint = sh(
                                        script: 'ssh-keygen -lf "$CHECK_KEY" | awk \'{print $2}\'',
                                        returnStdout: true
                                    ).trim()
                                    record(id, 'ssh', 'HAVE', "username=${env.CHECK_SSH_USER} fingerprint=${fingerprint}")
                                }
                            }
                        } catch (Exception ignored) {
                            record(id, 'ssh', 'MISSING', '(not found)')
                        }
                    }

                    echo 'Credential status summary:'
                    results.each { item ->
                        echo "${item.id} | ${item.type} | ${item.status} | ${item.detail}"
                    }

                    if (issueCount > 0) {
                        currentBuild.result = 'UNSTABLE'
                        echo "Credential check finished with ${issueCount} non-HAVE item(s)."
                    } else {
                        echo 'Credential check finished with all credentials in HAVE state.'
                    }
                }
            }
        }
    }
}
