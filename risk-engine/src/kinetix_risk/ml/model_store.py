import json
from pathlib import Path

import joblib
import torch

from kinetix_risk.ml.vol_predictor import VolatilityLSTM


class ModelStore:
    def __init__(self, base_dir: Path):
        self.base_dir = base_dir
        self.base_dir.mkdir(parents=True, exist_ok=True)
        self.manifest_path = self.base_dir / "manifest.json"

    def _load_manifest(self) -> dict:
        if self.manifest_path.exists():
            return json.loads(self.manifest_path.read_text())
        return {}

    def _save_manifest(self, manifest: dict) -> None:
        self.manifest_path.write_text(json.dumps(manifest, indent=2))

    def _update_manifest(self, key: str, version: str, filename: str, **extra: object) -> None:
        manifest = self._load_manifest()
        if key not in manifest:
            manifest[key] = {"versions": {}, "latest": None}
        entry: dict = {"filename": filename}
        entry.update(extra)
        manifest[key]["versions"][version] = entry
        manifest[key]["latest"] = version
        self._save_manifest(manifest)

    def save_vol_model(self, asset_class: str, model: VolatilityLSTM, version: str) -> Path:
        filename = f"vol_{asset_class}_{version}.pt"
        path = self.base_dir / filename
        torch.save(model.state_dict(), path)
        hidden_size = model.lstm.hidden_size
        num_layers = model.lstm.num_layers
        self._update_manifest(
            f"vol_{asset_class}", version, filename,
            hidden_size=hidden_size, num_layers=num_layers,
        )
        return path

    def load_vol_model(
        self,
        asset_class: str,
        version: str | None = None,
        hidden_size: int = 64,
        num_layers: int = 2,
    ) -> VolatilityLSTM:
        manifest = self._load_manifest()
        key = f"vol_{asset_class}"
        if key not in manifest:
            raise FileNotFoundError(f"No volatility model found for {asset_class}")
        if version is None:
            version = manifest[key]["latest"]
        if version not in manifest[key]["versions"]:
            raise FileNotFoundError(f"Version {version} not found for {asset_class}")
        entry = manifest[key]["versions"][version]
        # Support both old (string) and new (dict) manifest formats
        if isinstance(entry, dict):
            filename = entry["filename"]
            hidden_size = entry.get("hidden_size", hidden_size)
            num_layers = entry.get("num_layers", num_layers)
        else:
            filename = entry
        path = self.base_dir / filename
        model = VolatilityLSTM(input_size=1, hidden_size=hidden_size, num_layers=num_layers)
        model.load_state_dict(torch.load(path, weights_only=True))
        model.eval()
        return model

    def save_credit_model(self, model: object, version: str) -> Path:
        filename = f"credit_{version}.joblib"
        path = self.base_dir / filename
        joblib.dump(model, path)
        self._update_manifest("credit", version, filename)
        return path

    def load_credit_model(self, version: str | None = None) -> object:
        manifest = self._load_manifest()
        if "credit" not in manifest:
            raise FileNotFoundError("No credit model found")
        if version is None:
            version = manifest["credit"]["latest"]
        if version not in manifest["credit"]["versions"]:
            raise FileNotFoundError(f"Credit model version {version} not found")
        entry = manifest["credit"]["versions"][version]
        filename = entry["filename"] if isinstance(entry, dict) else entry
        return joblib.load(self.base_dir / filename)

    def save_anomaly_detector(self, detector: object, version: str) -> Path:
        filename = f"anomaly_{version}.joblib"
        path = self.base_dir / filename
        joblib.dump(detector, path)
        self._update_manifest("anomaly", version, filename)
        return path

    def load_anomaly_detector(self, version: str | None = None) -> object:
        manifest = self._load_manifest()
        if "anomaly" not in manifest:
            raise FileNotFoundError("No anomaly detector found")
        if version is None:
            version = manifest["anomaly"]["latest"]
        if version not in manifest["anomaly"]["versions"]:
            raise FileNotFoundError(f"Anomaly detector version {version} not found")
        entry = manifest["anomaly"]["versions"][version]
        filename = entry["filename"] if isinstance(entry, dict) else entry
        return joblib.load(self.base_dir / filename)
