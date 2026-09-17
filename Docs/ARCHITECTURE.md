Browser
│
├── Cognito
│
▼
Frontend
│
▼
API Gateway
│
▼
Backend / Lambda
│
├── S3
├── DynamoDB
└── Bedrock


```design
                       ┌─────────────┐
                       │   Cognito   │
                       └──────┬──────┘
                              │
                              ▼
┌──────────┐          ┌──────────────┐
│  React   │─────────▶│ API Gateway  │
└──────────┘          └───────┬──────┘
                              │
                    ┌─────────▼─────────┐
                    │ Backend API       │
                    └──────┬──────┬─────┘
                           │      │
                        metadata upload
                           │      │
                     ┌─────▼──┐ ┌─▼───────┐
                     │DynamoDB│ │   S3    │
                     └────────┘ └────┬─────┘
                                     │
                               async processing
                                     │
                                     ▼
                                 Bedrock
                                     │
                                     ▼
                              semantic retrieval
```