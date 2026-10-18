import json
import boto3
from main.db_manager import DatabaseManager

db_manager = DatabaseManager()

def broadcast_to_user_ui(user_id: int, message_payload: dict):
    """
    Placeholder/Client call for WebSockets or Push Notifications to User UI.
    """
    print(f"[WebSocket/Push] Broadcasting to User {user_id}: {json.dumps(message_payload)}")
    # Implement AWS API Gateway Management API or Firebase Cloud Messaging (FCM) here if applicable.

def lambda_handler(event, context):
    """
    Lambda 2: DB Logger & Push triggered by SNS (Success Topic 1 or Failure Topic 2).
    """
    for record in event.get('Records', []):
        try:
            # Extract SNS message payload
            sns_message_raw = record.get('Sns', {}).get('Message', '{}')
            payload = json.loads(sns_message_raw)

            user_id = payload.get('user_id')
            message_text = payload.get('message', 'Notification update regarding your request.')
            notification_type = payload.get('status', 'INFO')

            if not user_id:
                print(f"Received notification without user_id: {payload}")
                continue

            # 1. Database Logging into RDS Notification table
            notif_id = db_manager.add_notification(
                user_id=user_id,
                message=message_text,
                notification_type=notification_type
            )
            print(f"Logged notification ID {notif_id} for user ID {user_id}")

            # 2. Real-time Push to User UI
            broadcast_to_user_ui(user_id, {
                "notification_id": notif_id,
                "type": notification_type,
                "message": message_text
            })

        except Exception as e:
            print(f"Error handling notification event: {str(e)}")
            # Generally, DLQ/SNS subscriber errors can be logged or handled via DLQ for Lambda async invocations
            raise e

    return {"status": "Notifications logged and pushed successfully"}