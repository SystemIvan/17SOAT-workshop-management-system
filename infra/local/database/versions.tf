terraform {
  required_version = ">= 1.10"

  # "~> X.Y.0" accepts patch releases only; .terraform.lock.hcl records the exact version and checksums.
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 3.3.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9.0"
    }
  }
}
