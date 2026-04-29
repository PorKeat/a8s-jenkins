set shell := ["bash", "-cu"]

default:
    @just --list

deploy:
    @if ANSIBLE_LOCAL_TEMP=/private/tmp ansible-galaxy collection list community.general | grep -q '^community\.general[[:space:]]'; then \
        echo "community.general is already installed"; \
    else \
        ANSIBLE_LOCAL_TEMP=/private/tmp ansible-galaxy collection install -r collections/requirements.yml; \
    fi && \
    ./scripts/check-commit-safety.sh && \
    ANSIBLE_LOCAL_TEMP=/private/tmp ansible-playbook -i inventory.ini site.yml

destroy:
    ANSIBLE_LOCAL_TEMP=/private/tmp ansible-playbook -i inventory.ini site.yml -e jenkins_state=absent
