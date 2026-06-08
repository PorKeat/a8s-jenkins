import yaml
import json
import os

with open('config-secret.yml', 'r') as f:
    data = yaml.safe_load(f)

result = {
    "credentials": {
        "string": data.get('jenkins_string_credentials', []),
        "file": data.get('jenkins_file_credentials', []),
        "scm_token": data.get('jenkins_scm_token_credentials', []),
        "ssh_private_key": data.get('jenkins_ssh_private_key_credentials', []),
        "username_password": data.get('jenkins_username_password_credentials', [])
    }
}

os.makedirs('config.d', exist_ok=True)
with open('config.d/migrated-credentials.json', 'w') as f:
    json.dump(result, f, indent=2)

print("Migration successful.")
