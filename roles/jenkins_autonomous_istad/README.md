# jenkins_autonomous_istad

An Ansible Galaxy-style role that automates the Jenkins controller setup described in `setup.md`.

The role installs Jenkins on Debian/Ubuntu, installs the plugins called out in the notes in one bulk pass, applies Jenkins Configuration as Code (JCasC), creates the documented `trivy` SSH agent definition, configures the SonarQube Scanner tool installation, and seeds the application and deployment jobs documented from the legacy Jenkins.

- `a8s-backend`
- `a8s-frontend`
- `a8s-admin`

## Design choices

- Live secrets are not hardcoded into the role.
- Credential values are supplied through Ansible variables, ideally from Ansible Vault.
- The three application pipeline jobs are created from the exact repositories and branches documented in the notes.
- The tracked config now uses the public Jenkins URL `https://jenkins.autonomous-istad.com`.
- The install defaults follow the current official Jenkins Debian/Ubuntu guidance with Java 21 and the 2026 package signing key.
- Jenkins job definitions now live in `pipeline/jobs.yml`. SCM-backed jobs stay there as repository metadata, while only inline jobs use `pipeline/*.groovy`.
- The ready-to-run project config now provisions nginx in front of Jenkins so Cloudflare can reach the origin on ports `80` and `443`.

## Requirements

- Supported target OS: Debian/Ubuntu
- Collections: `community.general`

Install the required collection with:

```bash
ansible-galaxy collection install -r collections/requirements.yml
```

Ready-to-run project files are also included at the repository root:

- `config.yml` contains the target host plus tracked non-secret controller and node settings
- `config-secret.yml` is a gitignored local override for credentials and live secrets
- `config-secret-example.yml` is the tracked starter file for other users
- `pipeline/jobs.yml` contains Jenkins job and shared-library definitions
- `pipeline/*.groovy` only contains inline pipeline scripts, such as `deploy-pipeline`
- `inventory.ini` only provides a local Ansible entrypoint; the actual Jenkins target host is read from `config.yml`
- `site.yml` applies the role using `config.yml`, `pipeline/jobs.yml`, and then loads `config-secret.yml` when present
- `justfile` provides two simple commands: `deploy` and `destroy`
- `config-secret-example.yml` also includes optional placeholders for a provided origin certificate if you later want to replace the self-signed default with a Cloudflare Origin CA certificate

## Role variables

The most important variables are:

```yaml
jenkins_target_host: "34.87.139.220"
jenkins_target_user: "github-actions"
jenkins_release_channel: "lts"
jenkins_admin_username: "replace-me"
jenkins_admin_password: "replace-me"
jenkins_url: "https://jenkins.autonomous-istad.com"
jenkins_reverse_proxy_enabled: true
jenkins_reverse_proxy_tls_mode: "self_signed"
jenkins_username_password_credentials: []
jenkins_string_credentials: []
jenkins_ssh_private_key_credentials: []
```

Use `config.yml` for the target host and tracked non-secret settings, `pipeline/jobs.yml` for job definitions, and keep live values in `config-secret.yml`.

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
3. Edit `pipeline/jobs.yml` to change SCM-backed jobs, and edit `pipeline/*.groovy` only for inline jobs.
4. Copy `config-secret-example.yml` to `config-secret.yml`.
5. Fill the real credentials in `config-secret.yml`.
6. Deploy Jenkins:

```bash
just deploy
```

7. Remove Jenkins later if needed:

```bash
just destroy
```

## What the role configures

- Jenkins package, service, and Java
- Official Jenkins Debian/Ubuntu package installation using Java 21
- Bulk offline plugin installation with `jenkins-plugin-cli` when available, or the official Jenkins plugin manager jar as a fallback
- Jenkins Configuration as Code
- Confirmed plugins from the notes, plus the additional live plugins required by the current pipelines such as `timestamper` and `ws-cleanup`
- nginx reverse proxy on ports `80` and `443`, with a self-signed origin certificate by default so Cloudflare can connect immediately
- Built-in controller executors set to `2`
- `trivy` SSH agent definition pointing at `34.143.195.220` with remote path `/home/enz/jenkins`
- SonarQube Scanner tool installation named `sonar-scanner`
- Local admin user
- Jenkins credentials provided through vars
- Pipeline jobs for backend, frontend, admin, and the legacy deployment jobs
- Shared libraries required by the imported `deploy-pipeline`

## Notes

- The role intentionally does not embed the live credentials from `setup.md`.
- The tracked `config.yml` carries only non-secret settings, while `config-secret.yml` carries local live secrets.
- Job definitions live in `pipeline/jobs.yml`. Only inline Jenkins jobs keep Groovy files under `pipeline/*.groovy`.
- The remote server target is set in `config.yml` through `jenkins_target_host`, `jenkins_target_user`, `jenkins_target_port`, and `jenkins_target_python_interpreter`.
- The tracked config enables nginx with `jenkins_reverse_proxy_tls_mode: self_signed`. That is enough for Cloudflare `Full` mode, but switch to `provided` and store a real origin certificate in `config-secret.yml` if you want Cloudflare `Full (strict)`.
- `jenkins_release_channel: lts` installs the latest official Long Term Support Jenkins release. Switch it to `weekly` only if you explicitly want the newest weekly line.
- `just destroy` removes the Jenkins package, repository, keyring, and Jenkins data from the target machine. It does not remove the SSH access you use to reach that machine.
