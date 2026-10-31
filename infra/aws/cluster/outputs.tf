output "region" {
  description = "Region of the cluster."
  value       = var.region
}

output "cluster_name" {
  description = "Name of the EKS cluster."
  value       = aws_eks_cluster.this.name
}

output "cluster_endpoint" {
  description = "Kubernetes API endpoint; consumed by the database stack."
  value       = aws_eks_cluster.this.endpoint
}

output "cluster_ca_certificate" {
  description = "Base64 CA certificate of the API; consumed by the database stack."
  value       = aws_eks_cluster.this.certificate_authority[0].data
}

output "cluster_security_group_id" {
  description = "Security group EKS attaches to the managed nodes; the RDS security group allows 3306 from it."
  value       = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}

output "vpc_id" {
  description = "VPC where the database stack creates its security group."
  value       = aws_vpc.this.id
}

output "private_subnet_ids" {
  description = "Private subnets used by the RDS subnet group."
  value       = aws_subnet.private[*].id
}

output "update_kubeconfig_command" {
  description = "Command that merges the cluster credentials into ~/.kube/config."
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${aws_eks_cluster.this.name}"
}
