# DynamoDB Backend

The DynamoDB backend stores log level configuration in AWS DynamoDB tables. This backend is ideal for production environments where you need centralized configuration management, automatic expiration of debug loggers through TTL, and the ability to manage log levels across multiple services from a single location.

## Table Structure (v2.0+)

Each logger is stored as a separate item with a composite primary key, enabling TTL support:

- **Partition Key**: `service` (String) - e.g., "my-app"
- **Sort Key**: `logger` (String) - e.g., "com.example.Class1" or "root"
- **Attributes**:
  - `level` (String) - "DEBUG", "INFO", "WARN", "ERROR", etc.
  - `ttl` (Number) - Optional TTL timestamp for automatic expiration

Example items:
```json
{
  "service": "my-app",
  "logger": "com.example.Class1", 
  "level": "DEBUG",
  "ttl": 1672531200
}
{
  "service": "my-app",
  "logger": "root",
  "level": "WARN"
}
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DYNAMO_TABLE_NAME` | `log-levels` | DynamoDB table name |
| `SERVICE_NAME` | `default` | Service identifier |
| `AWS_REGION` | `us-east-1` | AWS region |

### AWS Credentials

The DynamoDB backend uses the AWS SDK's default credential provider chain:
1. Environment variables (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. Java system properties
3. Web Identity Token credentials
4. Credential profiles file
5. Amazon ECS container credentials
6. Amazon EC2 instance profile credentials

## Table Setup

### AWS CLI

```bash
# Create table with composite primary key
aws dynamodb create-table \
    --table-name log-levels \
    --attribute-definitions \
        AttributeName=service,AttributeType=S \
        AttributeName=logger,AttributeType=S \
    --key-schema \
        AttributeName=service,KeyType=HASH \
        AttributeName=logger,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

# Wait for table to be created
aws dynamodb wait table-exists \
    --table-name log-levels \
    --region us-east-1

# Enable TTL for automatic cleanup (optional)
aws dynamodb update-time-to-live \
    --table-name log-levels \
    --time-to-live-specification \
        Enabled=true,AttributeName=ttl \
    --region us-east-1
```

### CloudFormation

```yaml
AWSTemplateFormatVersion: '2010-09-09'
Description: 'DynamoDB table for DynaLog4J log level configuration'

Parameters:
  TableName:
    Type: String
    Default: log-levels
    Description: Name of the DynamoDB table
  
  EnableTTL:
    Type: String
    Default: 'true'
    AllowedValues: ['true', 'false']
    Description: Enable TTL for automatic log level expiration

Conditions:
  TTLEnabled: !Equals [!Ref EnableTTL, 'true']

Resources:
  LogLevelsTable:
    Type: AWS::DynamoDB::Table
    Properties:
      TableName: !Ref TableName
      BillingMode: PAY_PER_REQUEST
      AttributeDefinitions:
        - AttributeName: service
          AttributeType: S
        - AttributeName: logger
          AttributeType: S
      KeySchema:
        - AttributeName: service
          KeyType: HASH
        - AttributeName: logger
          KeyType: RANGE
      TimeToLiveSpecification:
        !If
          - TTLEnabled
          - AttributeName: ttl
            Enabled: true
          - !Ref 'AWS::NoValue'
      PointInTimeRecoverySpecification:
        PointInTimeRecoveryEnabled: true
      Tags:
        - Key: Application
          Value: DynaLog4J
        - Key: Purpose
          Value: LogLevelConfiguration

Outputs:
  TableName:
    Description: 'Name of the created DynamoDB table'
    Value: !Ref LogLevelsTable
    Export:
      Name: !Sub '${AWS::StackName}-TableName'
  
  TableArn:
    Description: 'ARN of the created DynamoDB table'
    Value: !GetAtt LogLevelsTable.Arn
    Export:
      Name: !Sub '${AWS::StackName}-TableArn'
```

### AWS CDK (TypeScript)

```typescript
import * as cdk from 'aws-cdk-lib';
import * as dynamodb from 'aws-cdk-lib/aws-dynamodb';
import { Construct } from 'constructs';

export interface DynaLog4JTableProps {
  readonly tableName?: string;
  readonly enableTTL?: boolean;
  readonly enablePointInTimeRecovery?: boolean;
}

export class DynaLog4JTable extends Construct {
  public readonly table: dynamodb.Table;

  constructor(scope: Construct, id: string, props: DynaLog4JTableProps = {}) {
    super(scope, id);

    this.table = new dynamodb.Table(this, 'LogLevelsTable', {
      tableName: props.tableName ?? 'log-levels',
      billingMode: dynamodb.BillingMode.PAY_PER_REQUEST,
      partitionKey: {
        name: 'service',
        type: dynamodb.AttributeType.STRING,
      },
      sortKey: {
        name: 'logger',
        type: dynamodb.AttributeType.STRING,
      },
      timeToLiveAttribute: props.enableTTL !== false ? 'ttl' : undefined,
      pointInTimeRecovery: props.enablePointInTimeRecovery ?? true,
      removalPolicy: cdk.RemovalPolicy.RETAIN,
    });

    // Add tags
    cdk.Tags.of(this.table).add('Application', 'DynaLog4J');
    cdk.Tags.of(this.table).add('Purpose', 'LogLevelConfiguration');
  }
}

// Usage example:
export class DynaLog4JStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    const logTable = new DynaLog4JTable(this, 'DynaLog4JTable', {
      tableName: 'my-app-log-levels',
      enableTTL: true,
      enablePointInTimeRecovery: true,
    });

    // Output the table name for reference
    new cdk.CfnOutput(this, 'TableName', {
      value: logTable.table.tableName,
      description: 'DynamoDB table name for DynaLog4J',
    });
  }
}
```

### AWS CDK (Python)

```python
from aws_cdk import (
    Stack,
    aws_dynamodb as dynamodb,
    CfnOutput,
    RemovalPolicy,
    Tags,
)
from constructs import Construct
from typing import Optional

class DynaLog4JTable(Construct):
    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        table_name: Optional[str] = None,
        enable_ttl: bool = True,
        enable_point_in_time_recovery: bool = True,
        **kwargs
    ) -> None:
        super().__init__(scope, construct_id, **kwargs)

        self.table = dynamodb.Table(
            self,
            "LogLevelsTable",
            table_name=table_name or "log-levels",
            billing_mode=dynamodb.BillingMode.PAY_PER_REQUEST,
            partition_key=dynamodb.Attribute(
                name="service",
                type=dynamodb.AttributeType.STRING
            ),
            sort_key=dynamodb.Attribute(
                name="logger",
                type=dynamodb.AttributeType.STRING
            ),
            time_to_live_attribute="ttl" if enable_ttl else None,
            point_in_time_recovery=enable_point_in_time_recovery,
            removal_policy=RemovalPolicy.RETAIN,
        )

        # Add tags
        Tags.of(self.table).add("Application", "DynaLog4J")
        Tags.of(self.table).add("Purpose", "LogLevelConfiguration")

class DynaLog4JStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, **kwargs) -> None:
        super().__init__(scope, construct_id, **kwargs)

        log_table = DynaLog4JTable(
            self,
            "DynaLog4JTable",
            table_name="my-app-log-levels",
            enable_ttl=True,
            enable_point_in_time_recovery=True,
        )

        CfnOutput(
            self,
            "TableName",
            value=log_table.table.table_name,
            description="DynamoDB table name for DynaLog4J",
        )
```

## Managing Log Levels

### Adding Log Levels

```bash
# Permanent log level override
aws dynamodb put-item \
    --table-name log-levels \
    --item '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.Class1"},
        "level": {"S": "DEBUG"}
    }' \
    --region us-east-1

# Temporary debug logger with TTL (expires in 1 hour)
EXPIRY_TIME=$(($(date +%s) + 3600))
aws dynamodb put-item \
    --table-name log-levels \
    --item '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.TempDebug"},
        "level": {"S": "DEBUG"},
        "ttl": {"N": "'$EXPIRY_TIME'"}
    }' \
    --region us-east-1
```

### Querying Log Levels

```bash
# Get all log levels for a service
aws dynamodb query \
    --table-name log-levels \
    --key-condition-expression 'service = :service' \
    --expression-attribute-values '{
        ":service": {"S": "my-app"}
    }' \
    --region us-east-1

# Get specific logger level
aws dynamodb get-item \
    --table-name log-levels \
    --key '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.Class1"}
    }' \
    --region us-east-1
```

### Updating Log Levels

```bash
# Update log level
aws dynamodb update-item \
    --table-name log-levels \
    --key '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.Class1"}
    }' \
    --update-expression 'SET #level = :level' \
    --expression-attribute-names '{
        "#level": "level"
    }' \
    --expression-attribute-values '{
        ":level": {"S": "WARN"}
    }' \
    --region us-east-1
```

### Deleting Log Levels

```bash
# Remove specific log level override
aws dynamodb delete-item \
    --table-name log-levels \
    --key '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.Class1"}
    }' \
    --region us-east-1
```

## Usage

### Command Line

```bash
# Set environment variables
export BACKEND=dynamodb
export DYNAMO_TABLE_NAME=log-levels
export SERVICE_NAME=my-app
export AWS_REGION=us-east-1

# Run DynaLog4J
java -jar target/DynaLog4J-1.0.0.jar \
     --backend dynamo \
     --table-name log-levels \
     --service-name my-app
```

## TTL (Time To Live) Support

TTL allows automatic expiration of log level overrides, which is particularly useful for temporary debug logging.

### Setting TTL

```bash
# Set debug level for 1 hour
EXPIRY_TIME=$(($(date +%s) + 3600))
aws dynamodb put-item \
    --table-name log-levels \
    --item '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.Debug"},
        "level": {"S": "DEBUG"},
        "ttl": {"N": "'$EXPIRY_TIME'"}
    }'

# Set debug level for 24 hours
EXPIRY_TIME=$(($(date +%s) + 86400))
aws dynamodb put-item \
    --table-name log-levels \
    --item '{
        "service": {"S": "my-app"},
        "logger": {"S": "com.example.LongDebug"},
        "level": {"S": "DEBUG"},
        "ttl": {"N": "'$EXPIRY_TIME'"}
    }'
```

### TTL Best Practices

1. **Use TTL for temporary overrides**: Set TTL for debug loggers that should automatically revert
2. **Permanent overrides**: Omit TTL attribute for permanent log level changes
3. **Monitor expiration**: DynamoDB TTL has up to 48-hour delay, plan accordingly
4. **Cleanup notifications**: Consider using DynamoDB Streams to track TTL deletions

## IAM Permissions

### Minimal Policy

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "dynamodb:Query"
            ],
            "Resource": "arn:aws:dynamodb:region:account:table/log-levels"
        }
    ]
}
```
