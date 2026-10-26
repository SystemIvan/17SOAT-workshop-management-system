provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Project     = "workshop-management-system"
      ManagedBy   = "terraform"
      Environment = "demo"
    }
  }
}
