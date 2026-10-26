#!/usr/bin/env bash
# Deletes the local kind cluster "wms" and everything in it, including the MySQL volume.
set -euo pipefail

kind delete cluster --name wms
