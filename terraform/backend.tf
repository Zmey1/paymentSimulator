terraform {
  backend "s3" {
    bucket         = "payments-sim-tfstate"
    key            = "terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "terraform-lock"
    encrypt        = true
  }
}
