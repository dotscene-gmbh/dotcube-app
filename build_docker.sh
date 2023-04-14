#!/bin/bash
set -o errtrace -o errexit -o nounset -o pipefail

# Ensure same user and group ids in the container as on the host/build system
USER_UID=$(id -u) USER_GID=$(id -g) docker compose build