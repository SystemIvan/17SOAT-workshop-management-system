variable "region" {
  description = "AWS region allowed by the Learner Lab (confirmed in CP8)."
  type        = string
  default     = "us-east-1"
}

variable "availability_zones" {
  description = "Two AZs: EKS and the RDS subnet group both require subnets in at least two zones."
  type        = list(string)
  # Fixed instead of discovered: us-east-1e does not host EKS control planes.
  default = ["us-east-1a", "us-east-1b"]
}

variable "cluster_name" {
  description = "Name of the EKS cluster; also used in subnet tags for load balancer discovery."
  type        = string
  default     = "workshop"
}

variable "kubernetes_version" {
  description = "EKS version; same minor as the local kind node (v1.35) so both environments run the same API."
  type        = string
  default     = "1.35"
}

variable "vpc_cidr" {
  description = "Address range of the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "One public subnet per AZ (load balancer and NAT gateway)."
  type        = list(string)
  default     = ["10.0.0.0/24", "10.0.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "One private subnet per AZ (worker nodes and RDS)."
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "node_instance_type" {
  description = "Worker node instance type allowed by the Learner Lab (2 vCPU, 4 GiB)."
  type        = string
  default     = "t3.medium"
}

variable "node_count" {
  description = "Fixed number of worker nodes; pods scale with the HPA (2-4), nodes do not autoscale."
  type        = number
  default     = 2
}

variable "node_disk_size" {
  description = "Root volume size of each worker node, in GiB."
  type        = number
  default     = 20
}
