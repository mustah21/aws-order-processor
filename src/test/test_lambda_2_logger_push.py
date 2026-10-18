
import json
import main.lambda_2_logger_push as notifier
import pytest


def make_event(payload):
    return {
        "Records": [
            {
                "Sns": {
                    "Message": json.dumps(payload)
                }
            }
        ]
    }


def test_valid_notification_is_logged(monkeypatch):
    captured = {}

    def fake_add_notification(**kwargs):
        captured.update(kwargs)
        return 100

    monkeypatch.setattr(
        notifier.db_manager, "add_notification", fake_add_notification
    )
    monkeypatch.setattr(
        notifier, "broadcast_to_user_ui", lambda *args: None
    )

    event = make_event({
        "user_id": 1,
        "message": "Your order has been placed.",
        "status": "SUCCESS",
    })

    result = notifier.lambda_handler(event, None)

    assert result == {
        "status": "Notifications logged and pushed successfully"
    }
    assert captured == {
        "user_id": 1,
        "message": "Your order has been placed.",
        "notification_type": "SUCCESS",
    }


def test_notification_is_broadcast_after_logging(monkeypatch):
    monkeypatch.setattr(
        notifier.db_manager, "add_notification", lambda **kwargs: 100
    )

    broadcasts = []
    monkeypatch.setattr(
        notifier,
        "broadcast_to_user_ui",
        lambda user_id, payload: broadcasts.append((user_id, payload)),
    )

    event = make_event({
        "user_id": 1,
        "message": "Order completed",
        "status": "SUCCESS",
    })

    notifier.lambda_handler(event, None)

    assert len(broadcasts) == 1
    user_id, payload = broadcasts[0]
    assert user_id == 1
    assert payload == {
        "notification_id": 100,
        "type": "SUCCESS",
        "message": "Order completed",
    }


def test_missing_user_id_is_skipped(monkeypatch):
    monkeypatch.setattr(
        notifier.db_manager,
        "add_notification",
        lambda **kwargs: pytest.fail("Should not write to database"),
    )
    monkeypatch.setattr(
        notifier,
        "broadcast_to_user_ui",
        lambda *args: pytest.fail("Should not broadcast"),
    )

    result = notifier.lambda_handler(
        make_event({"message": "No user", "status": "INFO"}), None
    )

    assert result["status"] == "Notifications logged and pushed successfully"


def test_default_message_and_type_are_used(monkeypatch):
    captured = {}

    def fake_add_notification(**kwargs):
        captured.update(kwargs)
        return 101

    monkeypatch.setattr(
        notifier.db_manager, "add_notification", fake_add_notification
    )
    monkeypatch.setattr(
        notifier, "broadcast_to_user_ui", lambda *args: None
    )

    notifier.lambda_handler(make_event({"user_id": 7}), None)

    assert captured["user_id"] == 7
    assert captured["message"] == (
        "Notification update regarding your request."
    )
    assert captured["notification_type"] == "INFO"


def test_database_error_is_reraised(monkeypatch):
    def fail_add_notification(**kwargs):
        raise RuntimeError("Database unavailable")

    monkeypatch.setattr(
        notifier.db_manager, "add_notification", fail_add_notification
    )

    with pytest.raises(RuntimeError, match="Database unavailable"):
        notifier.lambda_handler(
            make_event({"user_id": 1, "message": "Test"}), None
        )


def test_invalid_json_is_reraised():
    event = {
        "Records": [
            {"Sns": {"Message": "not valid JSON"}}
        ]
    }

    with pytest.raises(json.JSONDecodeError):
        notifier.lambda_handler(event, None)
