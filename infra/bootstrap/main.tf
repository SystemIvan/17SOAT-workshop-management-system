provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "workshop-management-system"
      ManagedBy   = "terraform"
      Environment = "bootstrap"
    }
  }
}

data "aws_caller_identity" "current" {
  lifecycle {
    postcondition {
      condition     = can(regex(":assumed-role/${var.allowed_role_name}/", self.arn))
      error_message = "Current session must assume role '${var.allowed_role_name}'. Refusing to create state bucket with wrong credentials."
    }
  }
}

resource "aws_s3_bucket" "state" {
  bucket        = "wms-tfstate-${data.aws_caller_identity.current.account_id}"
  force_destroy = false
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
