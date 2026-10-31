terraform {
  required_version = ">= 1.10"

  # "~> X.Y.0" accepts patch releases only; .terraform.lock.hcl records the exact version and checksums.
  required_providers {
    kind = {
      source  = "tehcyx/kind"
      version = "~> 0.11.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 3.3.0"
    }
  }
}
