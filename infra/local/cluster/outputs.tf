output "cluster_name" {
  description = "Name of the kind cluster."
  value       = kind_cluster.this.name
}

output "kubeconfig_context" {
  description = "kubectl context of the cluster; consumed by the database stack."
  value       = "kind-${kind_cluster.this.name}"
}

output "kubeconfig_path" {
  description = "Kubeconfig file that holds the cluster credentials."
  value       = kind_cluster.this.kubeconfig_path
}
