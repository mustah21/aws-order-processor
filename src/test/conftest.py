import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
SRC_DIR = PROJECT_ROOT / "src"
MAIN_DIR = SRC_DIR / "main"

sys.path.insert(0, str(SRC_DIR))
sys.path.insert(0, str(MAIN_DIR))

# Prevent creation of an SNS client during unit tests.
os.environ.pop("SNS_SUCCESS_TOPIC_ARN", None)
