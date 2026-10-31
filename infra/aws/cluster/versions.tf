terraform {
  required_version = ">= 1.10"

  # Remote state in the bucket described in infra/aws/bootstrap/README.md. The bucket name is not hard-coded (random
  # suffix): it is supplied with terraform init -backend-config="bucket=<bucket name>".
  # use_lockfile writes a .tflock object next to the state while an apply runs, so a second apply (for example
  # from the pipeline) is refused instead of corrupting the state; no DynamoDB table is needed.
  backend "s3" {
    key          = "aws/cluster.tfstate"
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
  }
}
