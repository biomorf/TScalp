#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if ! command -v ansible-galaxy >/dev/null 2>&1; then
  echo "ERROR: ansible-galaxy is not installed. Install Ansible first." >&2
  exit 1
fi

if ansible-galaxy collection list 2>/dev/null | grep -q "fedora.linux_system_roles"; then
  echo "Collection 'fedora.linux_system_roles' is already installed. Skipping."
else
  echo "Installing collections from requirements.yml..."
  ansible-galaxy collection install -r requirements.yml
  echo "Installation complete."
fi
