# AWS Order Processor

An event-driven, cloud-based order processing system built on AWS that automates the order workflow between wholesale food suppliers and their small business customers (cafes, restaurants, and catering companies). The system receives orders via API Gateway, queues them in SQS, processes them in Lambda, stores records in MariaDB (RDS), and pushes notifications through SNS .

**Key guarantee:** Every order is either processed successfully or preserved in the DLQ. DLQ has a set expiry timer for data to be erased. Erased data is not saved anywhere, for the sake of this project.

## The Problem

Wholesale suppliers in the food service industry communicate with numerous small business customers daily. Restaurant managers, cafe owners, and catering operators need timely, accurate information from their suppliers — but repetitive inquiries, manual updates, and back-and-forth messages create delays, misunderstandings, and unnecessary administrative work on both sides.

## Target Users

**Primary users — Wholesale food suppliers**

- Provide food products, ingredients, and supplies to cafes, restaurants, and catering companies
- Manage communication with many small business customers simultaneously
- Need to share order updates, delivery information, and important notices efficiently
- Benefit from automated notifications that replace repetitive manual messaging

**Secondary users — Small food service businesses**

- Cafe owners, restaurant managers, and catering operators
- Have busy daily schedules and limited time for administrative tasks
- Need timely, accurate information from their suppliers to plan operations
- Benefit from receiving automated notifications without needing to check messages or follow up on routine inquiries

**Example user scenario**

A cafe owner places an order for ingredients. The system processes it automatically, logs it in the database, and sends a success notification. If the order fails repeatedly, the DLQ captures it and a failure notification is sent — so the supplier can follow up without the cafe owner needing to chase updates.

## Key Benefits

- Automates routine order communication end-to-end
- Reduces time spent on customer communication by up to **90%**
- Reliable message handling via SQS queues and DLQ for failures
- Real-time notifications through SNS for both success and failure events
- Full audit trail stored in MariaDB
- Designed to be easy to use — no added complexity to daily workflows

## Architecture

> Refer to the architecture diagram:
> [`docs/architecture.jpeg`](docs/architecture.jpeg)

**Flow:**

1. **User UI** → **API Gateway** → **SQS Main Queue**
2. **Lambda 1 (Order Processor)** polls SQS, writes to **MariaDB (RDS)**, publishes success to **SNS 1**
3. On failure, SQS retries. After `maxReceiveCount`, message → **SQS DLQ** → **SNS 2 (Failure)**
4. **Lambda 2 (DB Logger & Push)** subscribes to SNS 1 & 2, logs to `Notification` table, pushes to User UI

---

## AWS Services

| Service       | Purpose                             |
| ------------- | ----------------------------------- |
| API Gateway   | Entry point for orders              |
| SQS (Main)    | Buffers incoming orders             |
| SQS (DLQ)     | Stores orders that fail repeatedly  |
| Lambda 1      | Processes orders, writes to MariaDB |
| Lambda 2      | Logs notifications, pushes to UI    |
| MariaDB (RDS) | Stores orders, items, notifications |
| SNS 1         | Success events                      |
| SNS 2         | Failure events                      |

**Region:** `us-east-1`

---

## Database Schema (MariaDB)

> Full schema image: [`docs/db_schema_image.png`](docs/db_schema_image.png)

| Table          | Key Columns                                    |
| -------------- | ---------------------------------------------- |
| `Customers`    | id (PK), Username, Password, email             |
| `Orders`       | id (PK), created_at, user_id (FK → Customers)  |
| `Items`        | id (PK), Name, image_url, price                |
| `order_items`  | id (PK), order_id (FK), item_id (FK), quantity |
| `Notification` | id (PK), user_id (FK), message, type           |

---

## Setup

1. **MariaDB (RDS):** Launch in `us-east-1`, load `sql/schema.sql`
2. **SQS:** Create `main-order-queue` with a DLQ and `maxReceiveCount = 3`
3. **SNS:** Create `order-success-topic` + `order-failure-topic`; subscribe `DBLoggerPush` to both
4. **VPC endpoint:** Create an Interface Endpoint for `com.amazonaws.us-east-1.sns` in the RDS VPC
5. **Lambda 1 (`order-processor`):** Python 3.12, `LabRole`, SQS trigger, `pymysql` layer, env vars set
6. **Lambda 2 (`DBLoggerPush`):** Python 3.12, `LabRole`, SNS triggers on both topics, `pymysql` layer, env vars set
7. **API Gateway:** `POST /orders` → SQS `main-order-queue`

> ⚠️ **Learner Lab reminder:** MariaDB RDS keeps costing money between sessions. Stop or delete it when not testing.

---

## Lambda configuration

All functions: Python 3.12, execution role `LabRole`, region `us-east-1`, RDS VPC
(`vpc-0b823582d7fd0c439`), security group `sg-099c10577814e67e1` (self-reference
inbound MySQL; VPC interface endpoint for SNS for outbound). Each package contains
its handler file, `db_manager.py`, and the `pymysql/` folder.

### Lambda 1: Order Processor (`order-processor`)

| Setting          | Value                                                                                                                                                                                                        |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Handler          | `lambda_function.lambda_handler`                                                                                                                                                                             |
| Trigger          | SQS `main-order-queue`                                                                                                                                                                                       |
| Memory / timeout | 128 MB / 10 s                                                                                                                                                                                                |
| Behavior         | Parses `{orderId, lines, simulate}`, saves the order to `orders` (status `DONE`) and `order_items` (prices read from `items`), then publishes to `order-success-topic`. Idempotent on `order_uuid` (unique). |

### Lambda 2: DB Logger and Push (`DBLoggerPush`)

| Setting          | Value                                                                                                             |
| ---------------- | ----------------------------------------------------------------------------------------------------------------- |
| Handler          | `lambda_function.lambda_handler`                                                                                  |
| Triggers         | SNS `order-success-topic` **and** SNS `order-failure-topic`                                                       |
| Memory / timeout | 128 MB / 10 s                                                                                                     |
| Behavior         | Writes `ORDER_DONE` / `ORDER_FAILED` rows to `notifications` and broadcasts a placeholder WebSocket/push message. |

### Read Lambda: `shop-api` (optional)

Handles `GET /items`, `GET /orders`, and `POST /login` behind API Gateway (Lambda proxy integration).

### Environment variables

| Name                    | Used by  | Value                               |
| ----------------------- | -------- | ----------------------------------- |
| `DB_HOST`               | all      | RDS endpoint                        |
| `DB_USER`               | all      | database user                       |
| `DB_PASSWORD`           | all      | set in the console, never committed |
| `DB_NAME`               | all      | `orders`                            |
| `DB_PORT`               | all      | `3306`                              |
| `SNS_SUCCESS_TOPIC_ARN` | Lambda 1 | ARN of `order-success-topic`        |

The test user (`TEST_USER_ID = 1`, customer `demo`) is hardcoded in Lambda 1 and Lambda 2.

---

## Failure & Retry Logic

- Lambda 1 raises an exception on failure (or when `simulate` requests it)
- SQS redelivers the message up to `maxReceiveCount = 3`
- After the third failure, the message moves to the **DLQ**
- The DLQ publishes to **SNS 2 (`order-failure-topic`)**
- Lambda 2 logs the failure and pushes a notification to the user

---

## Project Structure

```
AWS-Order-Processor/
├── app/
│   └── src/test/java/com/example/aws_g4/
│       ├── FakeShopApiTest.kt
│       ├── ShopRepositoryTest.kt
│       ├── FriendlyMessageTest.kt
│       └── SimulateModeTest.kt
├── src/
│   ├── main/
│   │   ├── db_manager.py
│   │   ├── lambda_1_processor.py
│   │   └── lambda_2_logger_push.py
│   └── test/
│       ├── conftest.py
│       ├── test_db_manager.py
│       ├── test_lambda_1_processor.py
│       └── test_lambda_2_logger_push.py

├── docs/
│   ├── architecture.jpeg
│   ├── db_schema_image.png
│   ├── docs/db_schema.webp
    ├── docs/Press_Release_V2.pdf
    ├── docs/Press_Release.pdf
    └── docs/requirements.txt
├── README.md

```

---

## Testing

The project has two test suites — one for the Python backend (pytest) and one for the Android frontend (JUnit). Both run without AWS credentials or a live database.

### Backend (pytest) — `src/test/`

```bash
python -m pytest -v
```

Covers `DatabaseManager` (order/item insert, idempotency, rollback, notifications), Lambda 1 (validation, POISON/TRANSIENT simulation, SNS publish, error propagation), and Lambda 2 (notification logging, broadcast, invalid input). All AWS/DB calls are **mocked**.

**Expected:** `23 passed in ~1s`

### Frontend (JUnit) — `app/src/test/java/com/example/aws_g4/`

```bash
./gradlew testDebugUnitTest
```

Covers `FakeShopApi` (catalog, idempotent orders, validation, seeded history), `ShopRepository` (success/failure paths), error message mapping, and `SimulateMode` values.

**Expected:** `BUILD SUCCESSFUL`

### AWS Integration (Manual)

| Step                   | Verify                                                 |
| ---------------------- | ------------------------------------------------------ |
| API Gateway → SQS      | `curl /orders` → message appears in `main-order-queue` |
| SQS → Lambda 1         | Log: `Saved order <uuid> -> DB id X`                   |
| Lambda 1 → RDS         | New rows in `orders` and `order_items`                 |
| Lambda 1 → SNS 1       | `order-success-topic` delivery count increments        |
| SNS 1 → Lambda 2       | Log: `Logged notification ID X`                        |
| Lambda 2 → RDS         | New row in `notifications` (type `SUCCESS`)            |
| Failure → DLQ          | `POISON` order retries 3× then lands in DLQ            |
| DLQ → SNS 2 → Lambda 2 | `notifications` row written with type `ORDER_FAILED`   |

---

## Team

| Name                   | Role                                |
| ---------------------- | ----------------------------------- |
| Su Kim                 | FullStack Developer		       |
| Mustafa Ahmad          | Backend Developer & Architecture    |
| Kiavash Montazeri      | Database and Lambda                 |
| Juan Valenzuela        | Architecture & coordination         |
| Jiya Kanhirathan Poyil | Testing and Documentation           |
