
import json
import lambda_1_processor as processor


def make_event(payload, attempts=1):
    return {
        "Records": [
            {
                "body": json.dumps(payload),
                "attributes": {
                    "ApproximateReceiveCount": str(attempts)
                },
            }
        ]
    }


def test_valid_order_is_saved(monkeypatch):
    create_order = lambda **kwargs: 42
    monkeypatch.setattr(processor.db_manager, "create_order", create_order)
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-123",
        "lines": [{"itemId": 1, "quantity": 2}],
    })

    result = processor.lambda_handler(event, None)

    assert result == {"status": "ok"}


def test_order_uses_hardcoded_user_id(monkeypatch):
    captured = {}

    def fake_create_order(**kwargs):
        captured.update(kwargs)
        return 42

    monkeypatch.setattr(
        processor.db_manager, "create_order", fake_create_order
    )
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-123",
        "lines": [{"itemId": 1, "quantity": 2}],
    })

    processor.lambda_handler(event, None)

    assert captured["user_id"] == processor.TEST_USER_ID
    assert captured["order_uuid"] == "order-123"
    assert captured["attempts"] == 1


def test_missing_order_id_raises_value_error(monkeypatch):
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "lines": [{"itemId": 1, "quantity": 1}]
    })

    import pytest
    with pytest.raises(ValueError, match="Missing"):
        processor.lambda_handler(event, None)


def test_empty_lines_raise_value_error(monkeypatch):
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-123",
        "lines": [],
    })

    import pytest
    with pytest.raises(ValueError, match="Missing"):
        processor.lambda_handler(event, None)


def test_poison_message_raises_error(monkeypatch):
    import pytest

    create_order = lambda **kwargs: (_ for _ in ()).throw(
        AssertionError("Database should not be called")
    )
    monkeypatch.setattr(processor.db_manager, "create_order", create_order)

    event = make_event({
        "orderId": "order-poison",
        "lines": [{"itemId": 1, "quantity": 1}],
        "simulate": "POISON",
    })

    with pytest.raises(RuntimeError, match="permanent failure"):
        processor.lambda_handler(event, None)


def test_transient_message_fails_before_third_attempt(monkeypatch):
    import pytest

    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-transient",
        "lines": [{"itemId": 1, "quantity": 1}],
        "simulate": "TRANSIENT",
    }, attempts=2)

    with pytest.raises(RuntimeError, match="temporary failure"):
        processor.lambda_handler(event, None)


def test_transient_message_succeeds_on_third_attempt(monkeypatch):
    captured = {}

    def fake_create_order(**kwargs):
        captured.update(kwargs)
        return 42

    monkeypatch.setattr(
        processor.db_manager, "create_order", fake_create_order
    )
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-transient",
        "lines": [{"itemId": 1, "quantity": 1}],
        "simulate": "TRANSIENT",
    }, attempts=3)

    result = processor.lambda_handler(event, None)

    assert result == {"status": "ok"}
    assert captured["attempts"] == 3


def test_sns_publishes_after_order_is_saved(monkeypatch):
    calls = []
    fake_sns = type("FakeSNS", (), {
        "publish": lambda self, **kwargs: calls.append(kwargs)
    })()

    monkeypatch.setattr(processor, "sns_client", fake_sns)
    monkeypatch.setattr(
        processor.db_manager, "create_order", lambda **kwargs: 42
    )

    event = make_event({
        "orderId": "order-123",
        "lines": [{"itemId": 1, "quantity": 1}],
    })

    processor.lambda_handler(event, None)

    assert len(calls) == 1
    message = json.loads(calls[0]["Message"])
    assert message["order_uuid"] == "order-123"
    assert message["order_id"] == 42
    assert message["status"] == "SUCCESS"


def test_sns_failure_does_not_fail_order(monkeypatch):
    class FailingSNS:
        def publish(self, **kwargs):
            raise RuntimeError("SNS unavailable")

    monkeypatch.setattr(processor, "sns_client", FailingSNS())
    monkeypatch.setattr(
        processor.db_manager, "create_order", lambda **kwargs: 42
    )

    event = make_event({
        "orderId": "order-123",
        "lines": [{"itemId": 1, "quantity": 1}],
    })

    result = processor.lambda_handler(event, None)

    assert result == {"status": "ok"}


def test_database_failure_is_raised(monkeypatch):
    import pytest

    def fail_create_order(**kwargs):
        raise RuntimeError("Database unavailable")

    monkeypatch.setattr(
        processor.db_manager, "create_order", fail_create_order
    )
    monkeypatch.setattr(processor, "sns_client", None)

    event = make_event({
        "orderId": "order-123",
        "lines": [{"itemId": 1, "quantity": 1}],
    })

    with pytest.raises(RuntimeError, match="Database unavailable"):
        processor.lambda_handler(event, None)
