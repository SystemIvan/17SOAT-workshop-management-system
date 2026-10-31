terraform {
  required_version = ">= 1.10"

  # Same bucket as the cluster stack, different key; see infra/aws/cluster/versions.tf.
  backend "s3" {
    key          = "aws/database.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }

  # "~> X.Y.0" accepts patch releases only; .terraform.lock.hcl records the exact version and checksums.
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.69.0"
    }
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
