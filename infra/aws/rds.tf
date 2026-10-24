terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "environment" {
  type    = string
  default = "production"
}

variable "db_password" {
  type        = string
  sensitive   = true
  description = "Master password for AWS RDS MySQL"
}

# --- VPC & Subnets ---
resource "aws_vpc" "tickethub_vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = {
    Name        = "tickethub-vpc-${var.environment}"
    Environment = var.environment
  }
}

resource "aws_subnet" "db_subnet_1" {
  vpc_id            = aws_vpc.tickethub_vpc.id
  cidr_block        = "10.0.10.0/24"
  availability_zone = "${var.aws_region}a"
  tags = { Name = "tickethub-db-subnet-1" }
}

resource "aws_subnet" "db_subnet_2" {
  vpc_id            = aws_vpc.tickethub_vpc.id
  cidr_block        = "10.0.11.0/24"
  availability_zone = "${var.aws_region}b"
  tags = { Name = "tickethub-db-subnet-2" }
}

resource "aws_db_subnet_group" "tickethub_db_subnet_group" {
  name       = "tickethub-db-subnet-group-${var.environment}"
  subnet_ids = [aws_subnet.db_subnet_1.id, aws_subnet.db_subnet_2.id]
  tags = { Name = "TicketHub RDS Subnet Group" }
}

# --- Security Group ---
resource "aws_security_group" "rds_sg" {
  name        = "tickethub-rds-sg-${var.environment}"
  description = "Control inbound traffic to TicketHub RDS MySQL"
  vpc_id      = aws_vpc.tickethub_vpc.id

  ingress {
    description = "MySQL from application microservices"
    from_port   = 3306
    to_port     = 3306
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# --- AWS RDS MySQL Multi-AZ Instance ---
resource "aws_db_instance" "tickethub_rds" {
  identifier             = "tickethub-mysql-${var.environment}"
  engine                 = "mysql"
  engine_version         = "8.4.3"
  instance_class         = "db.t3.medium"
  allocated_storage      = 50
  max_allocated_storage  = 200
  storage_type           = "gp3"
  multi_az               = true
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.tickethub_db_subnet_group.name
  vpc_security_group_ids = [aws_security_group.rds_sg.id]

  db_name  = "tickethub_core"
  username = "tickethub_admin"
  password = var.db_password

  backup_retention_period   = 7
  backup_window             = "03:00-04:00"
  maintenance_window        = "Sun:04:30-Sun:05:30"
  auto_minor_version_upgrade = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "tickethub-final-snapshot"
  deletion_protection       = true

  storage_encrypted = true

  tags = {
    Name        = "tickethub-rds-mysql"
    Environment = var.environment
  }
}

# --- S3 Media Storage Bucket ---
resource "aws_s3_bucket" "tickethub_media" {
  bucket = "tickethub-media-${var.environment}"
  tags = {
    Name        = "TicketHub Media Storage"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_versioning" "media_versioning" {
  bucket = aws_s3_bucket.tickethub_media.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media_encryption" {
  bucket = aws_s3_bucket.tickethub_media.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "media_public_access_block" {
  bucket                  = aws_s3_bucket.tickethub_media.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "media_lifecycle" {
  bucket = aws_s3_bucket.tickethub_media.id
  rule {
    id     = "transition-old-media"
    status = "Enabled"
    transition {
      days          = 90
      storage_class = "STANDARD_IA"
    }
  }
}

# --- Outputs ---
output "rds_endpoint" {
  value       = aws_db_instance.tickethub_rds.endpoint
  description = "The connection endpoint for the TicketHub RDS instance"
}

output "s3_bucket_name" {
  value       = aws_s3_bucket.tickethub_media.bucket
  description = "TicketHub S3 Media Storage Bucket"
}

