data "aws_caller_identity" "current" {
  lifecycle {
    postcondition {
      condition     = can(regex(":assumed-role/${var.allowed_role_name}/", self.arn))
      error_message = "Current session must assume role '${var.allowed_role_name}'. Refusing to plan or apply infrastructure with wrong credentials."
    }
  }
}

# The pre-existing LabRole provided by the AWS Academy Learner Lab environment.
# Both EKS cluster and managed node group use this role.
data "aws_iam_role" "lab_role" {
  name = "LabRole"
}
