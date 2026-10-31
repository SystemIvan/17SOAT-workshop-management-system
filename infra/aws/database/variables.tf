variable "region" {
  description = "AWS region allowed by the Learner Lab (confirmed in CP8)."
  type        = string
  default     = "us-east-1"
}

variable "state_bucket" {
  description = "State bucket (see infra/aws/bootstrap/README.md), used to read the cluster stack outputs."
  type        = string
}

variable "namespace" {
  description = "Namespace shared by the application objects; owned by this stack, not by Kustomize."
  type        = string
  default     = "workshop"
}

variable "mysql_engine_version" {
  description = "RDS MySQL major version; same major as the local StatefulSet (mysql:8.4). AWS picks the minor."
  type        = string
  default     = "8.4"
}

variable "instance_class" {
  description = "RDS instance class allowed by the Learner Lab."
  type        = string
  default     = "db.t3.micro"
}

variable "allocated_storage" {
  description = "RDS storage in GiB (gp3 minimum is 20)."
  type        = number
  default     = 20
}

variable "database_name" {
  description = "Schema created by RDS and migrated by Flyway."
  type        = string
  default     = "workshop"
}

variable "database_username" {
  description = "RDS master user, also used by the application (see the security note in main.tf)."
  type        = string
  default     = "workshop_user"
}
