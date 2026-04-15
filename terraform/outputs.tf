output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "rds_endpoint" {
  value = module.rds.rds_endpoint
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "jenkins_public_ip" {
  value = module.jenkins_ec2.public_ip
}

output "jenkins_role_arn" {
  value = module.jenkins_ec2.role_arn
}
