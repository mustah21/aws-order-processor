import json
import os
import boto3
from botocore.config import Config
from db_manager import DatabaseManager

db_manager = DatabaseManager()
TEST_USER_ID = 1  # hardcoded for the whole project
SUCCESS_TOPIC_ARN = os.environ.get("SNS_SUCCESS_TOPIC_ARN")

# Short timeouts so a missing VPC endpoint can't freeze the function.
sns_client = (
    boto3.client("sns", config=Config(connect_timeout=3, read_timeout=3,
                                      retries={"max_attempts": 1}))
    if SUCCESS_TOPIC_ARN else None
)


def lambda_handler(event, context):
    for record in event.get("Records", []):
        payload = json.loads(record["body"])
        order_uuid = payload.get("orderId")
        lines = payload.get("lines", [])
        if not order_uuid or not lines:
            raise ValueError("Missing 'orderId' or 'lines'")

        attempts = int(record["attributes"].get("ApproximateReceiveCount", 1))
        simulate = payload.get("simulate")

        # Failure simulation from the app's developer option
        if simulate == "POISON":
            raise RuntimeError("Simulated permanent failure")
        if simulate == "TRANSIENT" and attempts < 3:
            raise RuntimeError("Simulated temporary failure")

        order_id = db_manager.create_order(
            order_uuid=order_uuid, user_id=TEST_USER_ID, lines=lines, attempts=attempts
        )
        print(f"Saved order {order_uuid} -> DB id {order_id} (attempt {attempts})")

        # Success event to SNS 1. Never fail the order because of SNS.
        if sns_client:
            try:
                sns_client.publish(
                    TopicArn=SUCCESS_TOPIC_ARN,
                    Subject="Order Success Confirmation",
                    Message=json.dumps({
                        "order_uuid": order_uuid,
                        "order_id": order_id,
                        "user_id": TEST_USER_ID,
                        "status": "SUCCESS",
                        "message": f"Your order {order_uuid} has been placed.",
                    }),
                )
            except Exception as e:
                print(f"SNS publish failed for {order_uuid}: {e}")

    return {"status": "ok"}