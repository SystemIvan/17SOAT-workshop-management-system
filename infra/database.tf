resource "aws_db_subnet_group" "main" {
  name       = "${var.cluster_name}-db-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name = "${var.cluster_name}-db-subnets"
  }
}

resource "aws_security_group" "db" {
  name        = "${var.cluster_name}-rds-sg"
  description = "Security group for RDS MySQL, allowing 3306 only from the EKS cluster security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from EKS cluster pods"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_eks_cluster.main.vpc_config[0].cluster_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.cluster_name}-rds-sg"
  }
}

resource "aws_db_instance" "main" {
  identifier              = "${var.cluster_name}-db"
  allocated_storage       = 20
  max_allocated_storage   = 20
  storage_type            = "gp3"
  engine                  = "mysql"
  engine_version          = var.db_engine_version
  instance_class          = var.db_instance_class
  db_name                 = "workshop"
  username                = var.db_username
  password                = var.db_password
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.db.id]
  publicly_accessible     = false
  multi_az                = false
  skip_final_snapshot     = true
  deletion_protection     = false
  backup_retention_period = 0
  apply_immediately       = true

  tags = {
    Name = "${var.cluster_name}-db"
  }
}
