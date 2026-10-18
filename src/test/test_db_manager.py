import pymysql
import pytest
from unittest.mock import MagicMock

from db_manager import DatabaseManager


@pytest.fixture
def setup_database(monkeypatch):
    manager = DatabaseManager()
    connection = MagicMock()
    cursor = MagicMock()

    connection.cursor.return_value.__enter__.return_value = cursor
    monkeypatch.setattr(manager, "_get_connection", lambda: connection)

    return manager, connection, cursor


def test_create_order_saves_order_and_items(setup_database):
    manager, connection, cursor = setup_database
    cursor.lastrowid = 42
    cursor.fetchone.side_effect = [
        {"price": 4.50},
        {"price": 8.00},
    ]

    order_id = manager.create_order(
        order_uuid="order-123",
        user_id=1,
        lines=[
            {"itemId": 10, "quantity": 2},
            {"itemId": 20, "quantity": 1},
        ],
        attempts=1,
    )

    assert order_id == 42
    connection.commit.assert_called_once()
    connection.rollback.assert_not_called()
    connection.close.assert_called_once()

    executed_sql = [
        call.args[0] for call in cursor.execute.call_args_list
    ]

    assert any("INSERT INTO `orders`" in sql for sql in executed_sql)
    assert sum(
        "INSERT INTO `order_items`" in sql for sql in executed_sql
    ) == 2


def test_create_order_uses_database_item_prices(setup_database):
    manager, connection, cursor = setup_database
    cursor.lastrowid = 42
    cursor.fetchone.return_value = {"price": 4.50}

    manager.create_order(
        order_uuid="order-123",
        user_id=1,
        lines=[{"itemId": 10, "quantity": 2}],
    )

    item_insert = next(
        call for call in cursor.execute.call_args_list
        if "INSERT INTO `order_items`" in call.args[0]
    )

    assert item_insert.args[1] == (42, 10, 2, 4.50)
    connection.commit.assert_called_once()


def test_duplicate_order_returns_existing_id(setup_database):
    manager, connection, cursor = setup_database

    cursor.execute.side_effect = [
        pymysql.err.IntegrityError(1062, "Duplicate entry"),
        None,
    ]
    cursor.fetchone.return_value = {"id": 7}

    order_id = manager.create_order(
        order_uuid="order-existing",
        user_id=1,
        lines=[{"itemId": 10, "quantity": 1}],
    )

    assert order_id == 7
    connection.rollback.assert_called_once()
    connection.commit.assert_not_called()
    connection.close.assert_called_once()

    # The duplicate path should only execute the INSERT and SELECT.
    assert cursor.execute.call_count == 2


def test_unknown_item_rolls_back_order(setup_database):
    manager, connection, cursor = setup_database
    cursor.lastrowid = 42
    cursor.fetchone.return_value = None

    with pytest.raises(ValueError, match="Unknown item 999"):
        manager.create_order(
            order_uuid="order-123",
            user_id=1,
            lines=[{"itemId": 999, "quantity": 1}],
        )

    connection.rollback.assert_called_once()
    connection.commit.assert_not_called()
    connection.close.assert_called_once()


def test_database_error_rolls_back_and_closes(setup_database):
    manager, connection, cursor = setup_database
    cursor.execute.side_effect = RuntimeError("Database unavailable")

    with pytest.raises(RuntimeError, match="Database unavailable"):
        manager.create_order(
            order_uuid="order-123",
            user_id=1,
            lines=[{"itemId": 10, "quantity": 1}],
        )

    connection.rollback.assert_called_once()
    connection.commit.assert_not_called()
    connection.close.assert_called_once()


def test_add_notification_inserts_and_returns_id(setup_database):
    manager, connection, cursor = setup_database
    cursor.lastrowid = 55

    notification_id = manager.add_notification(
        user_id=1,
        message="Your order has been placed.",
        notification_type="SUCCESS",
    )

    assert notification_id == 55
    cursor.execute.assert_called_once_with(
        "INSERT INTO `notifications` (`user_id`, `message`, `type`) "
        "VALUES (%s, %s, %s)",
        (1, "Your order has been placed.", "SUCCESS"),
    )
    connection.commit.assert_called_once()
    connection.rollback.assert_not_called()
    connection.close.assert_called_once()


def test_add_notification_rolls_back_on_error(setup_database):
    manager, connection, cursor = setup_database
    cursor.execute.side_effect = RuntimeError("Insert failed")

    with pytest.raises(RuntimeError, match="Insert failed"):
        manager.add_notification(
            user_id=1,
            message="Test",
            notification_type="INFO",
        )

    connection.rollback.assert_called_once()
    connection.commit.assert_not_called()
    connection.close.assert_called_once()
