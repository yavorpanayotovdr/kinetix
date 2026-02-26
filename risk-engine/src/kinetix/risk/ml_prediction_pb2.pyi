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
