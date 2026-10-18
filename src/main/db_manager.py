import os
import pymysql
from datetime import datetime

class DatabaseManager:
    """
    Shared database utility class for RDS MySQL interactions within an AWS VPC.
    """
    def __init__(self):
        self.host = os.environ.get('DB_HOST')
        self.user = os.environ.get('DB_USER')
        self.password = os.environ.get('DB_PASSWORD')
        self.database = os.environ.get('DB_NAME')
        self.port = int(os.environ.get('DB_PORT', 3306))

    def _get_connection(self):
        return pymysql.connect(
            host=self.host,
            user=self.user,
            password=self.password,
            database=self.database,
            port=self.port,
            cursorclass=pymysql.cursors.DictCursor,
            connect_timeout=5
        )

    def create_order(self, order_uuid: str, user_id: int, lines: list, attempts: int = 1) -> int:
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                try:
                    cursor.execute(
                        "INSERT INTO `orders` (`order_uuid`, `user_id`, `status`, `attempts`) "
                        "VALUES (%s, %s, 'DONE', %s)",
                        (order_uuid, user_id, attempts),
                    )
                except pymysql.err.IntegrityError as e:
                    if e.args[0] == 1062:  # same orderId already saved: return it, no duplicate
                        connection.rollback()
                        cursor.execute("SELECT `id` FROM `orders` WHERE `order_uuid` = %s", (order_uuid,))
                        return cursor.fetchone()["id"]
                    raise
                order_id = cursor.lastrowid

                for line in lines:
                    cursor.execute("SELECT `price` FROM `items` WHERE `id` = %s", (line["itemId"],))
                    row = cursor.fetchone()
                    if row is None:
                        raise ValueError(f"Unknown item {line['itemId']}")
                    cursor.execute(
                        "INSERT INTO `order_items` (`order_id`, `item_id`, `quantity`, `unit_price`) "
                        "VALUES (%s, %s, %s, %s)",
                        (order_id, line["itemId"], line["quantity"], row["price"]),
                    )

            connection.commit()
            return order_id
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()

    def add_notification(self, user_id: int, message: str, notification_type: str) -> int:
        connection = self._get_connection()
        try:
            with connection.cursor() as cursor:
                query = "INSERT INTO `notifications` (`user_id`, `message`, `type`) VALUES (%s, %s, %s)"
                cursor.execute(query, (user_id, message, notification_type))
            connection.commit()
            return cursor.lastrowid
        except Exception as e:
            connection.rollback()
            raise e
        finally:
            connection.close()