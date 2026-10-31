provider "kubernetes" {
  config_path    = pathexpand(var.kubeconfig_path)
  config_context = var.kubeconfig_context
}

locals {
  mysql_labels = { app = "mysql" }
  # Same JDBC options as docker-compose; the host is the Service DNS name inside the cluster.
  db_url = join("", [
    "jdbc:mysql://mysql.${var.namespace}.svc.cluster.local:3306/${var.database_name}",
    "?useSSL=false&serverTimezone=America/Sao_Paulo&allowPublicKeyRetrieval=true",
  ])
}

resource "kubernetes_namespace_v1" "workshop" {
  metadata {
    name = var.namespace
  }
}

# Generated here and handed to the app through the workshop-db Secret, so the app password can never diverge
# from the real database password (technical-spec D6). Special characters are disabled because the value is
# interpolated into shell commands (readiness probe) and would need escaping.
resource "random_password" "root" {
  length  = 24
  special = false
}

resource "random_password" "app" {
  length  = 24
  special = false
}

resource "kubernetes_secret_v1" "mysql_root" {
  metadata {
    name      = "mysql-root"
    namespace = kubernetes_namespace_v1.workshop.metadata[0].name
  }
  data = {
    MYSQL_ROOT_PASSWORD = random_password.root.result
  }
}

# Connection contract consumed by the application through envFrom (technical-spec D1).
resource "kubernetes_config_map_v1" "workshop_db" {
  metadata {
    name      = "workshop-db"
    namespace = kubernetes_namespace_v1.workshop.metadata[0].name
  }
  data = {
    DB_URL = local.db_url
  }
}

resource "kubernetes_secret_v1" "workshop_db" {
  metadata {
    name      = "workshop-db"
    namespace = kubernetes_namespace_v1.workshop.metadata[0].name
  }
  data = {
    DB_USERNAME = var.database_username
    DB_PASSWORD = random_password.app.result
  }
}

resource "kubernetes_service_v1" "mysql" {
  metadata {
    name      = "mysql"
    namespace = kubernetes_namespace_v1.workshop.metadata[0].name
    labels    = local.mysql_labels
  }
  spec {
    type     = "ClusterIP"
    selector = local.mysql_labels
    port {
      name        = "mysql"
      port        = 3306
      target_port = 3306
    }
  }
}

resource "kubernetes_stateful_set_v1" "mysql" {
  metadata {
    name      = "mysql"
    namespace = kubernetes_namespace_v1.workshop.metadata[0].name
    labels    = local.mysql_labels
  }

  # apply only returns once mysql-0 is Ready: this is the pipeline's "database deploy" step.
  wait_for_rollout = true

  spec {
    service_name = kubernetes_service_v1.mysql.metadata[0].name
    replicas     = 1

    selector {
      match_labels = local.mysql_labels
    }

    template {
      metadata {
        labels = local.mysql_labels
      }

      spec {
        termination_grace_period_seconds = 30

        container {
          name  = "mysql"
          image = var.mysql_image
          # Experiment (CP5): trade crash durability for faster writes on this disposable local database.
          # The image entrypoint prepends mysqld to arguments that start with "--". Never applies to RDS.
          args = [
            "--skip-log-bin",
            "--innodb-flush-log-at-trx-commit=2",
            "--innodb-doublewrite=OFF",
          ]

          port {
            name           = "mysql"
            container_port = 3306
          }

          env {
            name  = "MYSQL_DATABASE"
            value = var.database_name
          }
          env {
            name = "MYSQL_ROOT_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.mysql_root.metadata[0].name
                key  = "MYSQL_ROOT_PASSWORD"
              }
            }
          }
          env {
            name = "MYSQL_USER"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.workshop_db.metadata[0].name
                key  = "DB_USERNAME"
              }
            }
          }
          env {
            name = "MYSQL_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret_v1.workshop_db.metadata[0].name
                key  = "DB_PASSWORD"
              }
            }
          }

          resources {
            requests = {
              cpu    = "250m"
              memory = "512Mi"
            }
            limits = {
              cpu    = "1000m"
              memory = "1Gi"
            }
          }

          # TCP to 127.0.0.1 fails while the entrypoint runs its temporary init server (--skip-networking),
          # so the pod only becomes Ready once the real server accepts connections. The password is read
          # from the container environment and never written into the pod spec.
          readiness_probe {
            exec {
              command = ["sh", "-c", "mysqladmin ping -h 127.0.0.1 -uroot -p\"$MYSQL_ROOT_PASSWORD\""]
            }
            initial_delay_seconds = 10
            period_seconds        = 5
            timeout_seconds       = 3
            failure_threshold     = 3
          }
          # No liveness probe on purpose: a crashed mysqld already restarts the container, and a liveness
          # probe that fires during the first-boot initialisation would kill it in a loop.

          volume_mount {
            name       = "data"
            mount_path = "/var/lib/mysql"
          }
        }
      }
    }

    # One PVC per replica (data-mysql-0); it outlives the pod, so data survives kubectl delete pod mysql-0.
    # It is removed together with the namespace on terraform destroy (disposable environment).
    volume_claim_template {
      metadata {
        name = "data"
      }
      spec {
        access_modes       = ["ReadWriteOnce"]
        storage_class_name = "standard"
        resources {
          requests = {
            storage = var.storage_size
          }
        }
      }
    }
  }

  timeouts {
    create = "10m"
    update = "10m"
  }
}
