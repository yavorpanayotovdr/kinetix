from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SectorInput(_message.Message):
    __slots__ = ("sector_label", "portfolio_weight", "benchmark_weight", "portfolio_return", "benchmark_return")
    SECTOR_LABEL_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_WEIGHT_FIELD_NUMBER: _ClassVar[int]
    BENCHMARK_WEIGHT_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_RETURN_FIELD_NUMBER: _ClassVar[int]
    BENCHMARK_RETURN_FIELD_NUMBER: _ClassVar[int]
    sector_label: str
    portfolio_weight: float
    benchmark_weight: float
    portfolio_return: float
    benchmark_return: float
    def __init__(self, sector_label: _Optional[str] = ..., portfolio_weight: _Optional[float] = ..., benchmark_weight: _Optional[float] = ..., portfolio_return: _Optional[float] = ..., benchmark_return: _Optional[float] = ...) -> None: ...

class AttributionPeriod(_message.Message):
    __slots__ = ("sectors", "total_benchmark_return")
    SECTORS_FIELD_NUMBER: _ClassVar[int]
    TOTAL_BENCHMARK_RETURN_FIELD_NUMBER: _ClassVar[int]
    sectors: _containers.RepeatedCompositeFieldContainer[SectorInput]
    total_benchmark_return: float
    def __init__(self, sectors: _Optional[_Iterable[_Union[SectorInput, _Mapping]]] = ..., total_benchmark_return: _Optional[float] = ...) -> None: ...

class BrinsonAttributionRequest(_message.Message):
    __slots__ = ("periods",)
    PERIODS_FIELD_NUMBER: _ClassVar[int]
    periods: _containers.RepeatedCompositeFieldContainer[AttributionPeriod]
    def __init__(self, periods: _Optional[_Iterable[_Union[AttributionPeriod, _Mapping]]] = ...) -> None: ...

class SectorAttributionResult(_message.Message):
    __slots__ = ("sector_label", "portfolio_weight", "benchmark_weight", "portfolio_return", "benchmark_return", "allocation_effect", "selection_effect", "interaction_effect", "total_active_contribution")
    SECTOR_LABEL_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_WEIGHT_FIELD_NUMBER: _ClassVar[int]
    BENCHMARK_WEIGHT_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_RETURN_FIELD_NUMBER: _ClassVar[int]
    BENCHMARK_RETURN_FIELD_NUMBER: _ClassVar[int]
    ALLOCATION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    SELECTION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    INTERACTION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    TOTAL_ACTIVE_CONTRIBUTION_FIELD_NUMBER: _ClassVar[int]
    sector_label: str
    portfolio_weight: float
    benchmark_weight: float
    portfolio_return: float
    benchmark_return: float
    allocation_effect: float
    selection_effect: float
    interaction_effect: float
    total_active_contribution: float
    def __init__(self, sector_label: _Optional[str] = ..., portfolio_weight: _Optional[float] = ..., benchmark_weight: _Optional[float] = ..., portfolio_return: _Optional[float] = ..., benchmark_return: _Optional[float] = ..., allocation_effect: _Optional[float] = ..., selection_effect: _Optional[float] = ..., interaction_effect: _Optional[float] = ..., total_active_contribution: _Optional[float] = ...) -> None: ...

class BrinsonAttributionResponse(_message.Message):
    __slots__ = ("sectors", "total_active_return", "total_allocation_effect", "total_selection_effect", "total_interaction_effect")
    SECTORS_FIELD_NUMBER: _ClassVar[int]
    TOTAL_ACTIVE_RETURN_FIELD_NUMBER: _ClassVar[int]
    TOTAL_ALLOCATION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    TOTAL_SELECTION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    TOTAL_INTERACTION_EFFECT_FIELD_NUMBER: _ClassVar[int]
    sectors: _containers.RepeatedCompositeFieldContainer[SectorAttributionResult]
    total_active_return: float
    total_allocation_effect: float
    total_selection_effect: float
    total_interaction_effect: float
    def __init__(self, sectors: _Optional[_Iterable[_Union[SectorAttributionResult, _Mapping]]] = ..., total_active_return: _Optional[float] = ..., total_allocation_effect: _Optional[float] = ..., total_selection_effect: _Optional[float] = ..., total_interaction_effect: _Optional[float] = ...) -> None: ...
