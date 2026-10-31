variable "kubeconfig_path" {
  description = "Kubeconfig written by the cluster stack."
  type        = string
  default     = "~/.kube/config"
}

variable "kubeconfig_context" {
  description = "kubectl context of the kind cluster (output kubeconfig_context of the cluster stack)."
  type        = string
  default     = "kind-workshop"
}

variable "namespace" {
  description = "Namespace shared by MySQL and the application; owned by this stack, not by Kustomize."
  type        = string
  default     = "workshop"
}

variable "mysql_image" {
  description = "MySQL image; same version used by docker-compose."
  type        = string
  default     = "mysql:8.4"
}

variable "database_name" {
  description = "Schema created by the MySQL entrypoint and migrated by Flyway."
  type        = string
  default     = "workshop"
}

variable "database_username" {
  description = "Application user (not root)."
  type        = string
  default     = "workshop_user"
}

variable "storage_size" {
  description = "Size of the MySQL persistent volume claim."
  type        = string
  default     = "1Gi"
}
