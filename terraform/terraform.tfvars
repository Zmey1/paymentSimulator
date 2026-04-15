aws_region             = "ap-south-1"
project_name           = "payments-sim"
vpc_cidr               = "10.0.0.0/16"
db_username            = "payments_user"
db_name                = "payments"
jenkins_key_name       = "payments-sim-key"
eks_node_instance_type = "t3.small"
jenkins_instance_type  = "m7i-flex.large"
operator_access_cidrs  = ["223.178.81.65/32"] # Replace with your public IP or campus CIDR
# db_password is passed via TF_VAR_db_password environment variable
