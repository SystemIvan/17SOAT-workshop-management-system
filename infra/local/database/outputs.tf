output "namespace" {
  description = "Namespace where the application must be deployed."
  value       = kubernetes_namespace_v1.workshop.metadata[0].name
}

output "db_url" {
  description = "JDBC URL published in the workshop-db ConfigMap."
  value       = local.db_url
}

output "db_config_map_name" {
  description = "ConfigMap with DB_URL, referenced by the app Deployment envFrom."
  value       = kubernetes_config_map_v1.workshop_db.metadata[0].name
}

output "db_secret_name" {
  description = "Secret with DB_USERNAME and DB_PASSWORD, referenced by the app Deployment envFrom."
  value       = kubernetes_secret_v1.workshop_db.metadata[0].name
}
