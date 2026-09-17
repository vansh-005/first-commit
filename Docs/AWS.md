AWS account: personal hackathon account

Primary region:
ap-south-1

Infrastructure:
AWS CDK only

Rules:
- Do not manually create infrastructure if CDK can manage it.
- Never delete AWS resources without asking.
- Check existing resources before creating duplicates.
- Prefer managed/serverless AWS services.
- Avoid NAT Gateway unless absolutely necessary.
- Keep cost minimal.
- Use CloudWatch logging.