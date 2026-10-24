# TicketHub — Cloud Production Architecture & Runbook

This guide details the production deployment architecture for TicketHub on AWS, covering AWS RDS, S3 Media Storage, and Secrets Manager.

---

## 1. AWS RDS MySQL Configuration

### Architecture
- **Engine**: MySQL 8.4 Community Edition
- **Deployment**: Multi-AZ standby replica in separate Availability Zones for 99.99% availability and automated failover.
- **Storage**: GP3 SSD (50 GB baseline, autoscaling up to 200 GB) with KMS-managed AES-256 encryption.
- **Connection Pool**: HikariCP with the `rds` profile:
  - Connection pooling tuned to 25 maximum active connections per service container.
  - SSL/TLS enforcement via `useSSL=true&requireSSL=true`.
  - Automatic reconnection and statement caching.

### Provisioning
Run Terraform from `infra/aws/`:
```bash
cd infra/aws
terraform init
terraform plan -out=tfplan -var="db_password=StrongMasterPassword123"
terraform apply tfplan
```

To activate the profile in Spring Boot microservices:
```bash
SPRING_PROFILES_ACTIVE=rds
DB_HOST=tickethub-mysql-production.c12345678.ap-south-1.rds.amazonaws.com
```

---

## 2. S3 Media Storage

### Architecture
- **Bucket**: `tickethub-media-production` with default server-side encryption (`AES256`) and object versioning.
- **Access Points**:
  - Event posters: `events/{eventId}/{uuid}-poster.jpg`
  - Screen seat maps: `screens/{screenId}/{uuid}-map.png`
  - Generic media: `general/{uuid}-asset.png`
- **Fallback Mode**: When running outside AWS or during local development, the service automatically falls back to local file storage under `uploads/media/`, requiring zero external cloud dependencies.

### Environment Variables
```bash
AWS_S3_ENABLED=true
AWS_S3_BUCKET=tickethub-media-production
AWS_S3_REGION=ap-south-1
```

---

## 3. Production Secret Management

Secrets are managed centrally in AWS Secrets Manager under secret key `tickethub/production/credentials`.

### Setup
Run the setup script:
```powershell
.\infra\aws\secrets-manager-setup.ps1 -Region "ap-south-1"
```
Or with bash:
```bash
./infra/aws/secrets-manager-setup.sh
```

### IAM Policy for ECS/EKS Task Execution Role
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:ap-south-1:*:secret:tickethub/production/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::tickethub-media-production/*"
    }
  ]
}
```
