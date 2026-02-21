from kinetix_risk.models import AssetClass, FrtbRiskClass

RISK_WEIGHTS: dict[FrtbRiskClass, float] = {
    FrtbRiskClass.GIRR: 0.015,
    FrtbRiskClass.CSR_NON_SEC: 0.03,
    FrtbRiskClass.CSR_SEC_CTP: 0.04,
    FrtbRiskClass.CSR_SEC_NON_CTP: 0.06,
    FrtbRiskClass.EQUITY: 0.20,
    FrtbRiskClass.COMMODITY: 0.15,
    FrtbRiskClass.FX: 0.10,
}

VEGA_RISK_WEIGHTS: dict[FrtbRiskClass, float] = {
    FrtbRiskClass.GIRR: 0.01,
    FrtbRiskClass.CSR_NON_SEC: 0.02,
    FrtbRiskClass.CSR_SEC_CTP: 0.03,
    FrtbRiskClass.CSR_SEC_NON_CTP: 0.04,
    FrtbRiskClass.EQUITY: 0.15,
    FrtbRiskClass.COMMODITY: 0.10,
    FrtbRiskClass.FX: 0.08,
}

INTRA_BUCKET_CORRELATION: dict[FrtbRiskClass, float] = {
    FrtbRiskClass.GIRR: 0.90,
    FrtbRiskClass.CSR_NON_SEC: 0.75,
    FrtbRiskClass.CSR_SEC_CTP: 0.70,
    FrtbRiskClass.CSR_SEC_NON_CTP: 0.65,
    FrtbRiskClass.EQUITY: 0.80,
    FrtbRiskClass.COMMODITY: 0.55,
    FrtbRiskClass.FX: 0.60,
}

INTER_BUCKET_CORRELATIONS: dict[str, float] = {
    "low": 0.5,
    "medium": 0.75,
    "high": 0.9,
}

_ASSET_CLASS_TO_RISK_CLASSES: dict[AssetClass, list[FrtbRiskClass]] = {
    AssetClass.EQUITY: [FrtbRiskClass.EQUITY],
    AssetClass.FIXED_INCOME: [FrtbRiskClass.GIRR, FrtbRiskClass.CSR_NON_SEC],
    AssetClass.FX: [FrtbRiskClass.FX],
    AssetClass.COMMODITY: [FrtbRiskClass.COMMODITY],
    AssetClass.DERIVATIVE: [FrtbRiskClass.EQUITY, FrtbRiskClass.FX],
}


def asset_class_to_risk_classes(asset_class: AssetClass) -> list[FrtbRiskClass]:
    return _ASSET_CLASS_TO_RISK_CLASSES.get(asset_class, [])
