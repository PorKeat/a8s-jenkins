set shell := ["bash", "-cu"]

default:
    @just --list

install:
    ansible-galaxy collection install -r collections/requirements.yml

check:
    ANSIBLE_LOCAL_TEMP=/private/tmp ansible-playbook --syntax-check -i inventory.ini site.yml

safe-config:
    ./scripts/check-commit-safety.sh

apply:
    ANSIBLE_LOCAL_TEMP=/private/tmp ansible-playbook -i inventory.ini site.yml
