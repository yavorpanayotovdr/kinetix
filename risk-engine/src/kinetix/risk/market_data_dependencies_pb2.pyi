from kinetix.common import types_pb2 as _types_pb2
from kinetix.risk import risk_calculation_pb2 as _risk_calculation_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class MarketDataDependency(_message.Message):
    __slots__ = ("data_type", "instrument_id", "asset_class", "required", "description", "parameters")
    class ParametersEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: str
        def __init__(self, key: _Optional[str] = ..., value: _Optional[str] = ...) -> None: ...
    DATA_TYPE_FIELD_NUMBER: _ClassVar[int]
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    REQUIRED_FIELD_NUMBER: _ClassVar[int]
    DESCRIPTION_FIELD_NUMBER: _ClassVar[int]
    PARAMETERS_FIELD_NUMBER: _ClassVar[int]
    data_type: _risk_calculation_pb2.MarketDataType
    instrument_id: str
    asset_class: str
    required: bool
    description: str
    parameters: _containers.ScalarMap[str, str]
    def __init__(self, data_type: _Optional[_Union[_risk_calculation_pb2.MarketDataType, str]] = ..., instrument_id: _Optional[str] = ..., asset_class: _Optional[str] = ..., required: bool = ..., description: _Optional[str] = ..., parameters: _Optional[_Mapping[str, str]] = ...) -> None: ...

class DataDependenciesRequest(_message.Message):
    __slots__ = ("positions", "calculation_type", "confidence_level")
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    CALCULATION_TYPE_FIELD_NUMBER: _ClassVar[int]
    CONFIDENCE_LEVEL_FIELD_NUMBER: _ClassVar[int]
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    calculation_type: _risk_calculation_pb2.RiskCalculationType
    confidence_level: _risk_calculation_pb2.ConfidenceLevel
    def __init__(self, positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ..., calculation_type: _Optional[_Union[_risk_calculation_pb2.RiskCalculationType, str]] = ..., confidence_level: _Optional[_Union[_risk_calculation_pb2.ConfidenceLevel, str]] = ...) -> None: ...

class DataDependenciesResponse(_message.Message):
    __slots__ = ("dependencies",)
    DEPENDENCIES_FIELD_NUMBER: _ClassVar[int]
    dependencies: _containers.RepeatedCompositeFieldContainer[MarketDataDependency]
    def __init__(self, dependencies: _Optional[_Iterable[_Union[MarketDataDependency, _Mapping]]] = ...) -> None: ...
