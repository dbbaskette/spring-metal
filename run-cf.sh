#!/usr/bin/env bash
set -euo pipefail

./mvnw clean package

cf push "$@"
