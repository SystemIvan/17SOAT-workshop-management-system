output "namespace" {
  description = "Namespace where the application must be deployed."
  value       = kubernetes_namespace_v1.workshop.metadata[0].name
}

output "db_url" {
  description = "JDBC URL published in the workshop-db ConfigMap."
  value       = local.db_url
}

output "db_instance_identifier" {
  description = "RDS instance identifier, to check in the console that it was removed after destroy."
  value       = aws_db_instance.this.identifier
}
