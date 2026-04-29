# jenkins_autonomous_istad

An Ansible Galaxy-style role that automates the Jenkins controller setup described in `setup.md`.

The role installs Jenkins on Debian/Ubuntu, installs the plugins called out in the notes, applies Jenkins Configuration as Code (JCasC), creates the documented `trivy` agent definition, and seeds the three repository-backed pipeline jobs:

- `a8s-backend`
- `a8s-frontend`
- `a8s-admin`

It also scaffolds the remaining named jobs from the notes as disabled placeholder pipeline jobs so the controller layout is reproducible without guessing missing SCM details:

- `deploy-microservices`
- `deploy-pipeline`
- `deploy-pipeliness`
- `deploy-service-test`
- `share_lib`
- `trivy`

## Design choices

- Live secrets are not hardcoded into the role.
- Credential values are supplied through Ansible variables, ideally from Ansible Vault.
- The three application pipeline jobs are created from the exact repositories and branches documented in the notes.
- Jobs with incomplete source details in the notes are created as disabled placeholders instead of inventing a pipeline implementation.

## Requirements

- Supported target OS: Debian/Ubuntu
- Collections: `community.general`

Install the required collection with:

```bash
ansible-galaxy collection install -r collections/requirements.yml
```

## Role variables

The most important variables are:

```yaml
jenkins_admin_username: "replace-me"
jenkins_admin_password: "replace-me"
jenkins_url: "https://jenkins.autonomous-istad.com"
jenkins_username_password_credentials: []
jenkins_string_credentials: []
jenkins_ssh_private_key_credentials: []
```

See `examples/jenkins-vars.yml.example` for a starter vars file you can move into Vault.

## Example playbook

```yaml
---
- name: Configure Jenkins
  hosts: jenkins
  become: true
  collections:
    - community.general
  vars_files:
    - vault/jenkins.yml
  roles:
    - role: jenkins_autonomous_istad
```

## What the role configures

- Jenkins package, service, and Java
- Jenkins Configuration as Code
- Confirmed plugins from the notes, including SonarQube and Pipeline plugins
- Built-in controller executors set to `2`
- `trivy` inbound agent definition with remote path `/home/enz/jenkins`
- Local admin user
- Jenkins credentials provided through vars
- Pipeline jobs for backend, frontend, and admin repositories
- Disabled placeholder jobs for the remaining job names from the notes

## Notes

- The role intentionally does not embed the live credentials from `setup.md`.
- `deploy-pipeline` is scaffolded as a placeholder because the notes describe its parameters and flow, but do not include enough source-of-truth SCM details to recreate the exact job safely.
