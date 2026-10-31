provider "aws" {
  region = var.region

  # Every resource carries these tags, which makes leftovers easy to find in the console after a destroy.
  default_tags {
    tags = {
      Project   = "workshop-management-system"
      Stack     = "aws-cluster"
      ManagedBy = "terraform"
    }
  }
}

# The Learner Lab forbids creating IAM roles; the pre-provisioned LabRole is used by both the control plane and
# the worker nodes.
data "aws_iam_role" "lab_role" {
  name = "LabRole"
}

### Network ###

resource "aws_vpc" "this" {
  cidr_block = var.vpc_cidr

  # Worker nodes need DNS resolution and hostnames to register with the cluster and to resolve the RDS endpoint.
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.cluster_name}-vpc" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.cluster_name}-igw" }
}

# The role/elb tag tells the AWS cloud controller where to place internet-facing load balancers
# (Service type LoadBalancer).
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name                                        = "${var.cluster_name}-public-${var.availability_zones[count.index]}"
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# Nodes and RDS live here: no public address, reachable only from inside the VPC.
resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name                                        = "${var.cluster_name}-private-${var.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "${var.cluster_name}-nat" }
}

# A single NAT gateway (not one per AZ) to save credit: if us-east-1a fails, private subnets lose outbound
# internet access. Accepted for a disposable lab environment.
resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
  tags          = { Name = "${var.cluster_name}-nat" }

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }
  tags = { Name = "${var.cluster_name}-public" }
}

# Outbound only: nodes pull images from GHCR/ECR through the NAT gateway; nothing can connect in.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this.id
  }
  tags = { Name = "${var.cluster_name}-private" }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

### EKS ###

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  version  = var.kubernetes_version
  role_arn = data.aws_iam_role.lab_role.arn

  vpc_config {
    subnet_ids = concat(aws_subnet.private[*].id, aws_subnet.public[*].id)
    # Public endpoint: kubectl on the developer PC and the GitHub runner reach the API (authentication is still
    # required). Private endpoint: nodes talk to the API inside the VPC instead of going out through the NAT.
    endpoint_public_access  = true
    endpoint_private_access = true
  }

  # Whoever creates the cluster (the Learner Lab voclabs role) becomes cluster admin through an access entry.
  # The pipeline uses credentials of the same role, so it gets the same access without extra configuration.
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }

  # Cost guard: a version that leaves standard support moves to extended support, which costs 6x per hour.
  upgrade_policy {
    support_type = "STANDARD"
  }
}

resource "aws_eks_node_group" "this" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "${var.cluster_name}-nodes"
  node_role_arn   = data.aws_iam_role.lab_role.arn
  subnet_ids      = aws_subnet.private[*].id

  instance_types = [var.node_instance_type]
  capacity_type  = "ON_DEMAND"
  ami_type       = "AL2023_x86_64_STANDARD"
  disk_size      = var.node_disk_size

  scaling_config {
    desired_size = var.node_count
    min_size     = var.node_count
    max_size     = var.node_count
  }

  update_config {
    max_unavailable = 1
  }

  # Nodes join the cluster only if they can reach ECR (system images) through the NAT route on first boot.
  depends_on = [aws_route_table_association.private]
}

# EKS add-on instead of the Helm chart used in kind: AWS keeps it compatible with the cluster version and the
# kubelet certificate is valid, so --kubelet-insecure-tls is not needed here.
resource "aws_eks_addon" "metrics_server" {
  cluster_name = aws_eks_cluster.this.name
  addon_name   = "metrics-server"

  # The add-on pods need running nodes; created earlier, the add-on would stay DEGRADED until timeout.
  depends_on = [aws_eks_node_group.this]
}
