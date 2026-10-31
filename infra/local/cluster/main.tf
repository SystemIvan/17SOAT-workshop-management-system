resource "kind_cluster" "this" {
  name           = var.cluster_name
  node_image     = var.node_image
  wait_for_ready = true

  # Merged into the user's default kubeconfig instead of the provider default (<name>-config in the working
  # directory), so no credential file is ever created inside the repository and kubectl works without flags.
  kubeconfig_path = pathexpand(var.kubeconfig_path)

  kind_config {
    kind        = "Cluster"
    api_version = "kind.x-k8s.io/v1alpha4"

    # A single control-plane node also runs workloads; enough for the local HPA range (1-3 replicas).
    node {
      role = "control-plane"

      # The node is a Docker container, so a NodePort is only reachable from Windows through these mappings.
      # Port-forward is not used because it pins traffic to one pod and would invalidate the HPA load test.
      extra_port_mappings {
        container_port = 30080
        host_port      = var.app_host_port
      }
      extra_port_mappings {
        container_port = 30025
        host_port      = var.mailpit_host_port
      }
    }
  }
}

# Configured from the cluster attributes (not from the kubeconfig file), which also makes every Helm release
# wait for the cluster to exist.
provider "helm" {
  kubernetes = {
    host                   = kind_cluster.this.endpoint
    client_certificate     = kind_cluster.this.client_certificate
    client_key             = kind_cluster.this.client_key
    cluster_ca_certificate = kind_cluster.this.cluster_ca_certificate
  }
}

resource "helm_release" "metrics_server" {
  name       = "metrics-server"
  repository = "https://kubernetes-sigs.github.io/metrics-server/"
  chart      = "metrics-server"
  version    = var.metrics_server_chart_version
  namespace  = "kube-system"

  # The kind kubelet serves a self-signed certificate without the node IP, which metrics-server rejects.
  # Skipping that verification is acceptable only in this disposable local cluster; EKS uses its add-on.
  values = [yamlencode({
    args = ["--kubelet-insecure-tls"]
  })]

  wait    = true
  timeout = 300
}
