import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials
import java.nio.charset.StandardCharsets
import org.jenkinsci.plugins.plaincredentials.FileCredentials
import org.jenkinsci.plugins.plaincredentials.StringCredentials

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

                    def currentById = SystemCredentialsProvider.getInstance()
                        .getCredentials()
                        .findAll { it?.id }
                        .collectEntries { [(it.id): it] }

                    def normalizeValue = { value ->
                        value == null ? '' : value.toString().trim()
                    }

                    // ✅ FIXED mask function (no takeRight)
                    def maskValue = { String value ->
                        if (!value) return '(empty)'

                        if (value.length() <= 8) {
                            if (value.length() <= 2) return value
                            return value[0] + ('*' * (value.length() - 2)) + value[-1]
                        }

                        def first = value.substring(0, 4)
                        def last = value.substring(value.length() - 4)
                        return first + '...' + last
                    }

                    def record = { String id, String type, String status, String detail ->
                        results << [id: id, type: type, status: status, detail: detail]
                        if (status != 'HAVE') {
                            issueCount++
                        }
                    }

                    def fileSecretAsString = { FileCredentials credential ->
                        try {
                            def bytes = credential?.secretBytes?.plainData
                            return bytes ? new String(bytes, StandardCharsets.UTF_8) : ''
                        } catch (Exception ignored) {
                            return ''
                        }
                    }

                    // ---------- STRING ----------
                    for (String id : stringCredentialIds) {
                        def credential = currentById[id]
                        if (!credential) {
                            record(id, 'string', 'MISSING', '(not found)')
                        } else if (!(credential instanceof StringCredentials)) {
                            record(id, 'string', 'WRONG_TYPE', credential.getClass().getName())
                        } else {
                            def value = normalizeValue(credential.secret?.plainText)
                            if (!value) {
                                record(id, 'string', 'EMPTY', '(empty value)')
                            } else if (value == 'replace-me') {
                                record(id, 'string', 'PLACEHOLDER', 'replace-me')
                            } else {
                                record(id, 'string', 'HAVE', maskValue(value))
                            }
                        }
                    }

                    // ---------- USERNAME/PASSWORD ----------
                    for (String id : usernamePasswordCredentialIds) {
                        def credential = currentById[id]
                        if (!credential) {
                            record(id, 'username/password', 'MISSING', '(not found)')
                        } else if (!(credential instanceof StandardUsernamePasswordCredentials)) {
                            record(id, 'username/password', 'WRONG_TYPE', credential.getClass().getName())
                        } else {
                            def username = normalizeValue(credential.username)
                            def password = normalizeValue(credential.password?.plainText)

                            if (!username || !password) {
                                record(id, 'username/password', 'EMPTY',
                                    "username=${username ?: '(empty)'} password=${password ? '(set)' : '(empty)'}")
                            } else if (username == 'replace-me' || password == 'replace-me') {
                                record(id, 'username/password', 'PLACEHOLDER',
                                    "username=${username} password=${maskValue(password)}")
                            } else {
                                record(id, 'username/password', 'HAVE',
                                    "username=${username} password=${maskValue(password)}")
                            }
                        }
                    }

                    // ---------- FILE ----------
                    for (String id : fileCredentialIds) {
                        def credential = currentById[id]
                        if (!credential) {
                            record(id, 'file', 'MISSING', '(not found)')
                        } else if (!(credential instanceof FileCredentials)) {
                            record(id, 'file', 'WRONG_TYPE', credential.getClass().getName())
                        } else {
                            def content = normalizeValue(fileSecretAsString(credential))

                            if (!content) {
                                record(id, 'file', 'EMPTY', '(empty file)')
                            } else if (content == 'replace-me') {
                                record(id, 'file', 'PLACEHOLDER', 'replace-me')
                            } else {
                                def lines = content.readLines().findAll { it?.trim() }
                                def keys = lines.collect { line ->
                                    line.contains('=') ? line.substring(0, line.indexOf('=')) : line
                                }.take(5)

                                record(id, 'file', 'HAVE',
                                    "lines=${lines.size()} keys=${keys.join(', ')}")
                            }
                        }
                    }

                    // ---------- SSH ----------
                    for (String id : sshCredentialIds) {
                        def credential = currentById[id]
                        if (!credential) {
                            record(id, 'ssh', 'MISSING', '(not found)')
                        } else if (!(credential instanceof SSHUserPrivateKey)) {
                            record(id, 'ssh', 'WRONG_TYPE', credential.getClass().getName())
                        } else {
                            def username = normalizeValue(credential.username)
                            def firstKey = ''

                            try {
                                firstKey = normalizeValue((credential.privateKeys ?: [''])[0])
                            } catch (Exception ignored) {}

                            if (!username || !firstKey) {
                                record(id, 'ssh', 'EMPTY',
                                    "username=${username ?: '(empty)'} key=${firstKey ? '(set)' : '(empty)'}")
                            } else if (username == 'replace-me' || firstKey.contains('replace-me')) {
                                record(id, 'ssh', 'PLACEHOLDER', "username=${username}")
                            } else {
                                def keyLine = firstKey.readLines().find { it && !it.startsWith('-----') } ?: firstKey
                                record(id, 'ssh', 'HAVE',
                                    "username=${username} key=${maskValue(keyLine)}")
                            }
                        }
                    }

                    // ---------- OUTPUT ----------
                    echo 'Credential status summary:'
                    results.each { item ->
                        echo "${item.id} | ${item.type} | ${item.status} | ${item.detail}"
                    }

                    if (issueCount > 0) {
                        currentBuild.result = 'UNSTABLE'
                        echo "Credential check finished with ${issueCount} issue(s)."
                    } else {
                        echo 'All credentials are in HAVE state.'
                    }
                }
            }
        }
    }
}