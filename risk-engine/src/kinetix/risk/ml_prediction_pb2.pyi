import datetime

from kinetix.common import types_pb2 as _types_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class VolatilityPredictionRequest(_message.Message):
    __slots__ = ("instrument_id", "asset_class", "returns_window", "model_version")
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    RETURNS_WINDOW_FIELD_NUMBER: _ClassVar[int]
    MODEL_VERSION_FIELD_NUMBER: _ClassVar[int]
    instrument_id: str
    asset_class: _types_pb2.AssetClass
    returns_window: _containers.RepeatedScalarFieldContainer[float]
    model_version: str
    def __init__(self, instrument_id: _Optional[str] = ..., asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., returns_window: _Optional[_Iterable[float]] = ..., model_version: _Optional[str] = ...) -> None: ...

class VolatilityPredictionResponse(_message.Message):
    __slots__ = ("instrument_id", "asset_class", "predicted_volatility", "model_version", "predicted_at")
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    PREDICTED_VOLATILITY_FIELD_NUMBER: _ClassVar[int]
    MODEL_VERSION_FIELD_NUMBER: _ClassVar[int]
    PREDICTED_AT_FIELD_NUMBER: _ClassVar[int]
    instrument_id: str
    asset_class: _types_pb2.AssetClass
    predicted_volatility: float
    model_version: str
    predicted_at: _timestamp_pb2.Timestamp
    def __init__(self, instrument_id: _Optional[str] = ..., asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., predicted_volatility: _Optional[float] = ..., model_version: _Optional[str] = ..., predicted_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class BatchVolatilityRequest(_message.Message):
    __slots__ = ("requests",)
    REQUESTS_FIELD_NUMBER: _ClassVar[int]
    requests: _containers.RepeatedCompositeFieldContainer[VolatilityPredictionRequest]
    def __init__(self, requests: _Optional[_Iterable[_Union[VolatilityPredictionRequest, _Mapping]]] = ...) -> None: ...

class BatchVolatilityResponse(_message.Message):
    __slots__ = ("predictions",)
    PREDICTIONS_FIELD_NUMBER: _ClassVar[int]
    predictions: _containers.RepeatedCompositeFieldContainer[VolatilityPredictionResponse]
    def __init__(self, predictions: _Optional[_Iterable[_Union[VolatilityPredictionResponse, _Mapping]]] = ...) -> None: ...

class CreditScoreRequest(_message.Message):
    __slots__ = ("issuer_id", "leverage_ratio", "interest_coverage", "debt_to_equity", "current_ratio", "revenue_growth", "volatility_90d", "market_value_log")
    ISSUER_ID_FIELD_NUMBER: _ClassVar[int]
    LEVERAGE_RATIO_FIELD_NUMBER: _ClassVar[int]
    INTEREST_COVERAGE_FIELD_NUMBER: _ClassVar[int]
    DEBT_TO_EQUITY_FIELD_NUMBER: _ClassVar[int]
    CURRENT_RATIO_FIELD_NUMBER: _ClassVar[int]
    REVENUE_GROWTH_FIELD_NUMBER: _ClassVar[int]
    VOLATILITY_90D_FIELD_NUMBER: _ClassVar[int]
    MARKET_VALUE_LOG_FIELD_NUMBER: _ClassVar[int]
    issuer_id: str
    leverage_ratio: float
    interest_coverage: float
    debt_to_equity: float
    current_ratio: float
    revenue_growth: float
    volatility_90d: float
    market_value_log: float
    def __init__(self, issuer_id: _Optional[str] = ..., leverage_ratio: _Optional[float] = ..., interest_coverage: _Optional[float] = ..., debt_to_equity: _Optional[float] = ..., current_ratio: _Optional[float] = ..., revenue_growth: _Optional[float] = ..., volatility_90d: _Optional[float] = ..., market_value_log: _Optional[float] = ...) -> None: ...

class CreditScoreResponse(_message.Message):
    __slots__ = ("issuer_id", "default_probability", "rating", "model_version", "scored_at")
    ISSUER_ID_FIELD_NUMBER: _ClassVar[int]
    DEFAULT_PROBABILITY_FIELD_NUMBER: _ClassVar[int]
    RATING_FIELD_NUMBER: _ClassVar[int]
    MODEL_VERSION_FIELD_NUMBER: _ClassVar[int]
    SCORED_AT_FIELD_NUMBER: _ClassVar[int]
    issuer_id: str
    default_probability: float
    rating: str
    model_version: str
    scored_at: _timestamp_pb2.Timestamp
    def __init__(self, issuer_id: _Optional[str] = ..., default_probability: _Optional[float] = ..., rating: _Optional[str] = ..., model_version: _Optional[str] = ..., scored_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class AnomalyDetectionRequest(_message.Message):
    __slots__ = ("metric_name", "metric_values")
    METRIC_NAME_FIELD_NUMBER: _ClassVar[int]
    METRIC_VALUES_FIELD_NUMBER: _ClassVar[int]
    metric_name: str
    metric_values: _containers.RepeatedScalarFieldContainer[float]
    def __init__(self, metric_name: _Optional[str] = ..., metric_values: _Optional[_Iterable[float]] = ...) -> None: ...

class AnomalyDetectionResponse(_message.Message):
    __slots__ = ("metric_name", "results")
    METRIC_NAME_FIELD_NUMBER: _ClassVar[int]
    RESULTS_FIELD_NUMBER: _ClassVar[int]
    metric_name: str
    results: _containers.RepeatedCompositeFieldContainer[AnomalyResult]
    def __init__(self, metric_name: _Optional[str] = ..., results: _Optional[_Iterable[_Union[AnomalyResult, _Mapping]]] = ...) -> None: ...

class AnomalyResult(_message.Message):
    __slots__ = ("is_anomaly", "anomaly_score", "metric_value")
    IS_ANOMALY_FIELD_NUMBER: _ClassVar[int]
    ANOMALY_SCORE_FIELD_NUMBER: _ClassVar[int]
    METRIC_VALUE_FIELD_NUMBER: _ClassVar[int]
    is_anomaly: bool
    anomaly_score: float
    metric_value: float
    def __init__(self, is_anomaly: bool = ..., anomaly_score: _Optional[float] = ..., metric_value: _Optional[float] = ...) -> None: ...

class RegimeSignalsProto(_message.Message):
    __slots__ = ("realised_vol_20d", "cross_asset_correlation", "credit_spread_bps", "pnl_volatility", "vol_of_vol", "credit_spread_present", "pnl_volatility_present")
    REALISED_VOL_20D_FIELD_NUMBER: _ClassVar[int]
    CROSS_ASSET_CORRELATION_FIELD_NUMBER: _ClassVar[int]
    CREDIT_SPREAD_BPS_FIELD_NUMBER: _ClassVar[int]
    PNL_VOLATILITY_FIELD_NUMBER: _ClassVar[int]
    VOL_OF_VOL_FIELD_NUMBER: _ClassVar[int]
    CREDIT_SPREAD_PRESENT_FIELD_NUMBER: _ClassVar[int]
    PNL_VOLATILITY_PRESENT_FIELD_NUMBER: _ClassVar[int]
    realised_vol_20d: float
    cross_asset_correlation: float
    credit_spread_bps: float
    pnl_volatility: float
    vol_of_vol: float
    credit_spread_present: bool
    pnl_volatility_present: bool
    def __init__(self, realised_vol_20d: _Optional[float] = ..., cross_asset_correlation: _Optional[float] = ..., credit_spread_bps: _Optional[float] = ..., pnl_volatility: _Optional[float] = ..., vol_of_vol: _Optional[float] = ..., credit_spread_present: bool = ..., pnl_volatility_present: bool = ...) -> None: ...

class RegimeThresholdsProto(_message.Message):
    __slots__ = ("normal_vol_ceiling", "elevated_vol_ceiling", "crisis_correlation_floor")
    NORMAL_VOL_CEILING_FIELD_NUMBER: _ClassVar[int]
    ELEVATED_VOL_CEILING_FIELD_NUMBER: _ClassVar[int]
    CRISIS_CORRELATION_FLOOR_FIELD_NUMBER: _ClassVar[int]
    normal_vol_ceiling: float
    elevated_vol_ceiling: float
    crisis_correlation_floor: float
    def __init__(self, normal_vol_ceiling: _Optional[float] = ..., elevated_vol_ceiling: _Optional[float] = ..., crisis_correlation_floor: _Optional[float] = ...) -> None: ...

class EarlyWarningProto(_message.Message):
    __slots__ = ("signal_name", "current_value", "threshold", "proximity_pct", "message")
    SIGNAL_NAME_FIELD_NUMBER: _ClassVar[int]
    CURRENT_VALUE_FIELD_NUMBER: _ClassVar[int]
    THRESHOLD_FIELD_NUMBER: _ClassVar[int]
    PROXIMITY_PCT_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    signal_name: str
    current_value: float
    threshold: float
    proximity_pct: float
    message: str
    def __init__(self, signal_name: _Optional[str] = ..., current_value: _Optional[float] = ..., threshold: _Optional[float] = ..., proximity_pct: _Optional[float] = ..., message: _Optional[str] = ...) -> None: ...

class RegimeDetectionRequest(_message.Message):
    __slots__ = ("signals", "thresholds", "current_regime", "consecutive_observations", "escalation_debounce", "de_escalation_debounce")
    SIGNALS_FIELD_NUMBER: _ClassVar[int]
    THRESHOLDS_FIELD_NUMBER: _ClassVar[int]
    CURRENT_REGIME_FIELD_NUMBER: _ClassVar[int]
    CONSECUTIVE_OBSERVATIONS_FIELD_NUMBER: _ClassVar[int]
    ESCALATION_DEBOUNCE_FIELD_NUMBER: _ClassVar[int]
    DE_ESCALATION_DEBOUNCE_FIELD_NUMBER: _ClassVar[int]
    signals: RegimeSignalsProto
    thresholds: RegimeThresholdsProto
    current_regime: str
    consecutive_observations: int
    escalation_debounce: int
    de_escalation_debounce: int
    def __init__(self, signals: _Optional[_Union[RegimeSignalsProto, _Mapping]] = ..., thresholds: _Optional[_Union[RegimeThresholdsProto, _Mapping]] = ..., current_regime: _Optional[str] = ..., consecutive_observations: _Optional[int] = ..., escalation_debounce: _Optional[int] = ..., de_escalation_debounce: _Optional[int] = ...) -> None: ...

class RegimeDetectionResponse(_message.Message):
    __slots__ = ("regime", "confidence", "is_confirmed", "consecutive_observations", "degraded_inputs", "early_warnings", "detected_at", "correlation_anomaly_score")
    REGIME_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_FIELD_NUMBER: _ClassVar[int]
    IS_CONFIRMED_FIELD_NUMBER: _ClassVar[int]
    CONSECUTIVE_OBSERVATIONS_FIELD_NUMBER: _ClassVar[int]
    DEGRADED_INPUTS_FIELD_NUMBER: _ClassVar[int]
    EARLY_WARNINGS_FIELD_NUMBER: _ClassVar[int]
    DETECTED_AT_FIELD_NUMBER: _ClassVar[int]
    CORRELATION_ANOMALY_SCORE_FIELD_NUMBER: _ClassVar[int]
    regime: str
    confidence: float
    is_confirmed: bool
    consecutive_observations: int
    degraded_inputs: bool
    early_warnings: _containers.RepeatedCompositeFieldContainer[EarlyWarningProto]
    detected_at: _timestamp_pb2.Timestamp
    correlation_anomaly_score: float
    def __init__(self, regime: _Optional[str] = ..., confidence: _Optional[float] = ..., is_confirmed: bool = ..., consecutive_observations: _Optional[int] = ..., degraded_inputs: bool = ..., early_warnings: _Optional[_Iterable[_Union[EarlyWarningProto, _Mapping]]] = ..., detected_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., correlation_anomaly_score: _Optional[float] = ...) -> None: ...
