output "bucket_name" {
  description = "Name of the S3 bucket created for Terraform remote state"
  value       = aws_s3_bucket.state.id
}

output "region" {
  description = "AWS region of the state bucket"
  value       = var.region
}
