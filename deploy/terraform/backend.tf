# Uncomment and configure for remote state storage
# terraform {
#   backend "s3" {
#     bucket         = "kinetix-terraform-state"
#     key            = "eks/terraform.tfstate"
#     region         = "us-east-1"
#     dynamodb_table = "kinetix-terraform-locks"
#     encrypt        = true
#   }
# }
