variable "region" {
  description = "AWS region for the demo infrastructure"
  type        = string
  default     = "us-east-1"
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
  default     = "wms-demo"
}

variable "kubernetes_version" {
  description = "Kubernetes version for the EKS cluster (standard support)"
  type        = string
  default     = "1.30"
}

variable "node_instance_type" {
  description = "EC2 instance type for EKS managed node group"
  type        = string
  default     = "t3.medium"
}

variable "node_count" {
  description = "Fixed number of nodes in the EKS node group"
  type        = number
  default     = 2
}

variable "db_instance_class" {
  description = "Instance class for RDS MySQL"
  type        = string
  default     = "db.t3.micro"
}

variable "db_engine_version" {
  description = "MySQL engine version for RDS"
  type        = string
  default     = "8.4"
}

variable "db_username" {
  description = "Master username for RDS MySQL"
  type        = string
  default     = "workshop_app"
}

variable "db_password" {
  description = "Master password for RDS MySQL (sensitive)"
  type        = string
  sensitive   = true
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.40.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones used for the VPC subnets"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b"]
}

variable "allowed_role_name" {
  description = "Expected IAM role substring in caller identity (safety guard)"
  type        = string
  default     = "voclabs"
}
