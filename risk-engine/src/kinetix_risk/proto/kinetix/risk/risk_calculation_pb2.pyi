import datetime

from kinetix.common import types_pb2 as _types_pb2
from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class RiskCalculationType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    RISK_CALCULATION_TYPE_UNSPECIFIED: _ClassVar[RiskCalculationType]
    HISTORICAL: _ClassVar[RiskCalculationType]
    PARAMETRIC: _ClassVar[RiskCalculationType]
    MONTE_CARLO: _ClassVar[RiskCalculationType]

class ConfidenceLevel(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    CONFIDENCE_LEVEL_UNSPECIFIED: _ClassVar[ConfidenceLevel]
    CL_95: _ClassVar[ConfidenceLevel]
    CL_99: _ClassVar[ConfidenceLevel]
RISK_CALCULATION_TYPE_UNSPECIFIED: RiskCalculationType
HISTORICAL: RiskCalculationType
PARAMETRIC: RiskCalculationType
MONTE_CARLO: RiskCalculationType
CONFIDENCE_LEVEL_UNSPECIFIED: ConfidenceLevel
CL_95: ConfidenceLevel
CL_99: ConfidenceLevel

class VaRRequest(_message.Message):
    __slots__ = ("portfolio_id", "calculation_type", "confidence_level", "time_horizon_days", "num_simulations", "positions")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    CALCULATION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    TIME_HORIZON_DAYS_FIELD_NUMBER: _ClassVar[int]
    NUM_SIMULATIONS_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    calculation_type: RiskCalculationType
    confidence_level: ConfidenceLevel
    time_horizon_days: int
    num_simulations: int
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., calculation_type: _Optional[_Union[RiskCalculationType, str]] = ..., confidence_level: _Optional[_Union[ConfidenceLevel, str]] = ..., time_horizon_days: _Optional[int] = ..., num_simulations: _Optional[int] = ..., positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ...) -> None: ...

class VaRResponse(_message.Message):
    __slots__ = ("portfolio_id", "calculation_type", "confidence_level", "var_value", "expected_shortfall", "component_breakdown", "calculated_at")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    CALCULATION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    VAR_VALUE_FIELD_NUMBER: _ClassVar[int]
    EXPECTED_SHORTFALL_FIELD_NUMBER: _ClassVar[int]
    COMPONENT_BREAKDOWN_FIELD_NUMBER: _ClassVar[int]
    CALCULATED_AT_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    calculation_type: RiskCalculationType
    confidence_level: ConfidenceLevel
    var_value: float
    expected_shortfall: float
    component_breakdown: _containers.RepeatedCompositeFieldContainer[VaRComponentBreakdown]
    calculated_at: _timestamp_pb2.Timestamp
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., calculation_type: _Optional[_Union[RiskCalculationType, str]] = ..., confidence_level: _Optional[_Union[ConfidenceLevel, str]] = ..., var_value: _Optional[float] = ..., expected_shortfall: _Optional[float] = ..., component_breakdown: _Optional[_Iterable[_Union[VaRComponentBreakdown, _Mapping]]] = ..., calculated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class VaRComponentBreakdown(_message.Message):
    __slots__ = ("asset_class", "var_contribution", "percentage_of_total")
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    VAR_CONTRIBUTION_FIELD_NUMBER: _ClassVar[int]
    PERCENTAGE_OF_TOTAL_FIELD_NUMBER: _ClassVar[int]
    asset_class: _types_pb2.AssetClass
    var_contribution: float
    percentage_of_total: float
    def __init__(self, asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., var_contribution: _Optional[float] = ..., percentage_of_total: _Optional[float] = ...) -> None: ...
