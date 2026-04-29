# jenkins_autonomous_istad

An Ansible Galaxy-style role that automates the Jenkins controller setup described in `setup.md`.

The role installs Jenkins on Debian/Ubuntu, installs the plugins called out in the notes, applies Jenkins Configuration as Code (JCasC), creates the documented `trivy` agent definition, and seeds the three repository-backed pipeline jobs:

- `a8s-backend`
- `a8s-frontend`
- `a8s-admin`

It also currently scaffolds the remaining named jobs from the notes as disabled placeholder pipeline jobs:

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
- The tracked config now uses the public Jenkins URL `https://jenkins.autonomous-istad.com`.
- The install defaults follow the current official Jenkins Debian/Ubuntu guidance with Java 21 and the 2026 package signing key.

## Requirements

- Supported target OS: Debian/Ubuntu
- Collections: `community.general`

Install the required collection with:

```bash
ansible-galaxy collection install -r collections/requirements.yml
```

Ready-to-run project files are also included at the repository root:

- `config.yml` contains the target host plus tracked non-secret controller, job, and node settings
- `config-secret.yml` is a gitignored local override for credentials and live secrets
- `config-secret-example.yml` is the tracked starter file for other users
- `inventory.ini` only provides a local Ansible entrypoint; the actual Jenkins target host is read from `config.yml`
- `site.yml` applies the role using `config.yml` and then loads `config-secret.yml` when present
- `justfile` provides two simple commands: `deploy` and `destroy`

## Role variables

The most important variables are:

```yaml
jenkins_target_host: "34.87.139.220"
jenkins_target_user: "ubuntu"
jenkins_release_channel: "weekly"
jenkins_admin_username: "replace-me"
jenkins_admin_password: "replace-me"
jenkins_url: "https://jenkins.autonomous-istad.com"
jenkins_username_password_credentials: []
jenkins_string_credentials: []
jenkins_ssh_private_key_credentials: []
```

Use `config.yml` for the target host and tracked non-secret settings, and keep live values in `config-secret.yml`.

## Example playbook

```yaml
---
- name: Configure Jenkins
  hosts: jenkins_target
  become: true
  collections:
    - community.general
  vars_files:
    - config.yml
  roles:
    - role: jenkins_autonomous_istad
```

## Run it against `34.87.139.220`

1. Edit `config.yml` and set `jenkins_target_host` and `jenkins_target_user` for your server.
2. Keep `config.yml` committed with non-secret settings only.
3. Copy `config-secret-example.yml` to `config-secret.yml`.
4. Fill the real credentials in `config-secret.yml`.
5. Deploy Jenkins:

```bash
just deploy
```

6. Remove Jenkins later if needed:

```bash
just destroy
```

## What the role configures

- Jenkins package, service, and Java
- Official Jenkins Debian/Ubuntu package installation using Java 21
- Jenkins Configuration as Code
- Confirmed plugins from the notes, plus the additional live plugins required by the current pipelines such as `timestamper` and `ws-cleanup`
- Built-in controller executors set to `2`
- `trivy` inbound agent definition with remote path `/home/enz/jenkins`
- Local admin user
- Jenkins credentials provided through vars
- Pipeline jobs for backend, frontend, and admin repositories
- Disabled placeholder jobs for the remaining job names from the notes

## Notes

- The role intentionally does not embed the live credentials from `setup.md`.
- The tracked `config.yml` carries only non-secret settings, while `config-secret.yml` carries local live secrets.
- The remote server target is set in `config.yml` through `jenkins_target_host`, `jenkins_target_user`, `jenkins_target_port`, and `jenkins_target_python_interpreter`.
- The Jenkins public URL is set to `https://jenkins.autonomous-istad.com`, but this role does not provision the reverse proxy or TLS certificate.
- `jenkins_release_channel: weekly` installs the latest official weekly Jenkins release; set it to `lts` if you want the latest Long Term Support release instead.
- `just destroy` removes the Jenkins package, repository, keyring, and Jenkins data from the target machine. It does not remove the SSH access you use to reach that machine.
- `deploy-pipeline` is scaffolded as a placeholder because the notes describe its parameters and flow, but do not include enough source-of-truth SCM details to recreate the exact job safely.
