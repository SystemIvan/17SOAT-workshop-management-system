variable "cluster_name" {
  description = "Name of the kind cluster; the kubeconfig context becomes kind-<cluster_name>."
  type        = string
  default     = "workshop"
}

variable "node_image" {
  description = "kindest/node image pinned by digest, so every machine and the pipeline run the same Kubernetes."
  type        = string
  # Default image of the kind library (v0.31.0) embedded in tehcyx/kind 0.11.0.
  default = "kindest/node:v1.35.0@sha256:452d707d4862f52530247495d180205e029056831160e22870e37e3f6c1ac31f"
}

variable "kubeconfig_path" {
  description = "Kubeconfig file where the cluster credentials are merged (outside the repository)."
  type        = string
  default     = "~/.kube/config"
}

variable "app_host_port" {
  description = "Host port mapped to the API NodePort (30080)."
  type        = number
  default     = 8080
}

variable "mailpit_host_port" {
  description = "Host port mapped to the Mailpit web UI NodePort (30025)."
  type        = number
  default     = 8025
}

variable "metrics_server_chart_version" {
  description = "Version of the official metrics-server Helm chart (kubernetes-sigs)."
  type        = string
  default     = "3.14.0"
}
