locals {
  repositories = [
    "payment-service",
    "fraud-detection-service",
    "notification-service",
    "payment-dashboard"
  ]
}

resource "aws_ecr_repository" "services" {
  for_each             = toset(local.repositories)
  name                 = "${var.project_name}/${each.value}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = false
  }

  tags = {
    Name = "${var.project_name}-${each.value}"
  }
}
