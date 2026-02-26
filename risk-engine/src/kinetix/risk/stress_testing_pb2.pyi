import datetime

from kinetix.common import types_pb2 as _types_pb2
from kinetix.risk import risk_calculation_pb2 as _risk_calculation_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class StressTestRequest(_message.Message):
    __slots__ = ("portfolio_id", "scenario_name", "calculation_type", "confidence_level", "time_horizon_days", "positions", "vol_shocks", "price_shocks", "description")
    class VolShocksEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: float
        def __init__(self, key: _Optional[str] = ..., value: _Optional[float] = ...) -> None: ...
    class PriceShocksEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: float
        def __init__(self, key: _Optional[str] = ..., value: _Optional[float] = ...) -> None: ...
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    SCENARIO_NAME_FIELD_NUMBER: _ClassVar[int]
    CALCULATION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    TIME_HORIZON_DAYS_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    VOL_SHOCKS_FIELD_NUMBER: _ClassVar[int]
    PRICE_SHOCKS_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    scenario_name: str
    calculation_type: _risk_calculation_pb2.RiskCalculationType
    confidence_level: _risk_calculation_pb2.ConfidenceLevel
    time_horizon_days: int
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    vol_shocks: _containers.ScalarMap[str, float]
    price_shocks: _containers.ScalarMap[str, float]
    description: str
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., scenario_name: _Optional[str] = ..., calculation_type: _Optional[_Union[_risk_calculation_pb2.RiskCalculationType, str]] = ..., confidence_level: _Optional[_Union[_risk_calculation_pb2.ConfidenceLevel, str]] = ..., time_horizon_days: _Optional[int] = ..., positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ..., vol_shocks: _Optional[_Mapping[str, float]] = ..., price_shocks: _Optional[_Mapping[str, float]] = ..., description: _Optional[str] = ...) -> None: ...

class AssetClassImpact(_message.Message):
    __slots__ = ("asset_class", "base_exposure", "stressed_exposure", "pnl_impact")
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    BASE_EXPOSURE_FIELD_NUMBER: _ClassVar[int]
    STRESSED_EXPOSURE_FIELD_NUMBER: _ClassVar[int]
    PNL_IMPACT_FIELD_NUMBER: _ClassVar[int]
    asset_class: _types_pb2.AssetClass
    base_exposure: float
    stressed_exposure: float
    pnl_impact: float
    def __init__(self, asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., base_exposure: _Optional[float] = ..., stressed_exposure: _Optional[float] = ..., pnl_impact: _Optional[float] = ...) -> None: ...

class StressTestResponse(_message.Message):
    __slots__ = ("scenario_name", "base_var", "stressed_var", "pnl_impact", "asset_class_impacts", "calculated_at")
    SCENARIO_NAME_FIELD_NUMBER: _ClassVar[int]
    BASE_VAR_FIELD_NUMBER: _ClassVar[int]
    STRESSED_VAR_FIELD_NUMBER: _ClassVar[int]
    PNL_IMPACT_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_IMPACTS_FIELD_NUMBER: _ClassVar[int]
    CALCULATED_AT_FIELD_NUMBER: _ClassVar[int]
    scenario_name: str
    base_var: float
    stressed_var: float
    pnl_impact: float
    asset_class_impacts: _containers.RepeatedCompositeFieldContainer[AssetClassImpact]
    calculated_at: _timestamp_pb2.Timestamp
    def __init__(self, scenario_name: _Optional[str] = ..., base_var: _Optional[float] = ..., stressed_var: _Optional[float] = ..., pnl_impact: _Optional[float] = ..., asset_class_impacts: _Optional[_Iterable[_Union[AssetClassImpact, _Mapping]]] = ..., calculated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class ListScenariosRequest(_message.Message):
    __slots__ = ()
    def __init__(self) -> None: ...

class ListScenariosResponse(_message.Message):
    __slots__ = ("scenario_names",)
    SCENARIO_NAMES_FIELD_NUMBER: _ClassVar[int]
    scenario_names: _containers.RepeatedScalarFieldContainer[str]
    def __init__(self, scenario_names: _Optional[_Iterable[str]] = ...) -> None: ...

class GreeksRequest(_message.Message):
    __slots__ = ("portfolio_id", "calculation_type", "confidence_level", "time_horizon_days", "positions")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    CALCULATION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    TIME_HORIZON_DAYS_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    calculation_type: _risk_calculation_pb2.RiskCalculationType
    confidence_level: _risk_calculation_pb2.ConfidenceLevel
    time_horizon_days: int
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., calculation_type: _Optional[_Union[_risk_calculation_pb2.RiskCalculationType, str]] = ..., confidence_level: _Optional[_Union[_risk_calculation_pb2.ConfidenceLevel, str]] = ..., time_horizon_days: _Optional[int] = ..., positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ...) -> None: ...

class StressGreekValues(_message.Message):
    __slots__ = ("asset_class", "delta", "gamma", "vega")
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    DELTA_FIELD_NUMBER: _ClassVar[int]
    GAMMA_FIELD_NUMBER: _ClassVar[int]
    VEGA_FIELD_NUMBER: _ClassVar[int]
    asset_class: _types_pb2.AssetClass
    delta: float
    gamma: float
    vega: float
    def __init__(self, asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., delta: _Optional[float] = ..., gamma: _Optional[float] = ..., vega: _Optional[float] = ...) -> None: ...

class GreeksResponse(_message.Message):
    __slots__ = ("portfolio_id", "asset_class_greeks", "theta", "rho", "calculated_at")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_GREEKS_FIELD_NUMBER: _ClassVar[int]
    THETA_FIELD_NUMBER: _ClassVar[int]
    RHO_FIELD_NUMBER: _ClassVar[int]
    CALCULATED_AT_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: str
    asset_class_greeks: _containers.RepeatedCompositeFieldContainer[StressGreekValues]
    theta: float
    rho: float
    calculated_at: _timestamp_pb2.Timestamp
    def __init__(self, portfolio_id: _Optional[str] = ..., asset_class_greeks: _Optional[_Iterable[_Union[StressGreekValues, _Mapping]]] = ..., theta: _Optional[float] = ..., rho: _Optional[float] = ..., calculated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
