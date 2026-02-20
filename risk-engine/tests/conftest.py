import sys
from pathlib import Path

# Add generated proto stubs to Python path
proto_path = Path(__file__).parent.parent / "src" / "kinetix_risk" / "proto"
if proto_path.exists() and str(proto_path) not in sys.path:
    sys.path.insert(0, str(proto_path))
