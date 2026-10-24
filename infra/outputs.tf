output "cluster_name" {
  description = "Name of the provisioned EKS cluster"
  value       = aws_eks_cluster.main.name
}

output "ecr_repository_url" {
  description = "URL of the ECR container repository"
  value       = aws_ecr_repository.app.repository_url
}

output "db_endpoint" {
  description = "Endpoint (host:port) of the RDS MySQL instance"
  value       = aws_db_instance.main.endpoint
}

output "region" {
  description = "AWS region of the deployment"
  value       = var.region
}
