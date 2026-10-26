variable "region" {
  description = "AWS region for the remote state S3 bucket"
  type        = string
  default     = "us-east-1"
}

variable "allowed_role_name" {
  description = "Expected IAM role name substring in caller identity (safety guard)"
  type        = string
  default     = "voclabs"
}
