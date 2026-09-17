Read docs/PRODUCT.md first.
Read docs/ARCHITECTURE.md before changing infrastructure.

Use Java/Spring Boot for backend.
Use React for frontend.
Use Java CDK for infrastructure.

Primary AWS region is ap-south-1.

Never:
- hardcode AWS credentials
- commit secrets
- manually create infra outside CDK
- delete resources without approval
- change architecture without updating docs

Before implementation:
1. inspect existing code
2. explain intended changes
3. make focused changes
4. run tests
5. run cdk synth if infrastructure changed