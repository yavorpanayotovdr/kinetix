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

class LiquidityTier(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    LIQUIDITY_TIER_UNSPECIFIED: _ClassVar[LiquidityTier]
    HIGH_LIQUID: _ClassVar[LiquidityTier]
    LIQUID: _ClassVar[LiquidityTier]
    SEMI_LIQUID: _ClassVar[LiquidityTier]
    ILLIQUID: _ClassVar[LiquidityTier]
LIQUIDITY_TIER_UNSPECIFIED: LiquidityTier
HIGH_LIQUID: LiquidityTier
LIQUID: LiquidityTier
SEMI_LIQUID: LiquidityTier
ILLIQUID: LiquidityTier

class LiquidityInput(_message.Message):
    __slots__ = ("instrument_id", "market_value", "adv", "adv_missing", "adv_staleness_days", "asset_class", "bid_ask_spread_bps", "adv_updated_at")
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    MARKET_VALUE_FIELD_NUMBER: _ClassVar[int]
    ADV_FIELD_NUMBER: _ClassVar[int]
    ADV_MISSING_FIELD_NUMBER: _ClassVar[int]
    ADV_STALENESS_DAYS_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    BID_ASK_SPREAD_BPS_FIELD_NUMBER: _ClassVar[int]
    ADV_UPDATED_AT_FIELD_NUMBER: _ClassVar[int]
    instrument_id: str
    market_value: float
    adv: float
    adv_missing: bool
    adv_staleness_days: int
    asset_class: _types_pb2.AssetClass
    bid_ask_spread_bps: float
    adv_updated_at: str
    def __init__(self, instrument_id: _Optional[str] = ..., market_value: _Optional[float] = ..., adv: _Optional[float] = ..., adv_missing: bool = ..., adv_staleness_days: _Optional[int] = ..., asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., bid_ask_spread_bps: _Optional[float] = ..., adv_updated_at: _Optional[str] = ...) -> None: ...

class LiquidityAdjustedVaRRequest(_message.Message):
    __slots__ = ("book_id", "base_var", "base_holding_period", "inputs", "stress_factors", "portfolio_daily_vol")
    class StressFactorsEntry(_message.Message):
        __slots__ = ("key", "value")
        KEY_FIELD_NUMBER: _ClassVar[int]
        VALUE_FIELD_NUMBER: _ClassVar[int]
        key: str
        value: float
        def __init__(self, key: _Optional[str] = ..., value: _Optional[float] = ...) -> None: ...
    BOOK_ID_FIELD_NUMBER: _ClassVar[int]
    BASE_VAR_FIELD_NUMBER: _ClassVar[int]
    BASE_HOLDING_PERIOD_FIELD_NUMBER: _ClassVar[int]
    INPUTS_FIELD_NUMBER: _ClassVar[int]
    STRESS_FACTORS_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_DAILY_VOL_FIELD_NUMBER: _ClassVar[int]
    book_id: _types_pb2.BookId
    base_var: float
    base_holding_period: int
    inputs: _containers.RepeatedCompositeFieldContainer[LiquidityInput]
    stress_factors: _containers.ScalarMap[str, float]
    portfolio_daily_vol: float
    def __init__(self, book_id: _Optional[_Union[_types_pb2.BookId, _Mapping]] = ..., base_var: _Optional[float] = ..., base_holding_period: _Optional[int] = ..., inputs: _Optional[_Iterable[_Union[LiquidityInput, _Mapping]]] = ..., stress_factors: _Optional[_Mapping[str, float]] = ..., portfolio_daily_vol: _Optional[float] = ...) -> None: ...

class PositionLiquidityRisk(_message.Message):
    __slots__ = ("instrument_id", "asset_class", "market_value", "tier", "horizon_days", "adv", "adv_missing", "adv_stale", "lvar_contribution", "stressed_liquidation_value", "concentration_status")
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    MARKET_VALUE_FIELD_NUMBER: _ClassVar[int]
    TIER_FIELD_NUMBER: _ClassVar[int]
    HORIZON_DAYS_FIELD_NUMBER: _ClassVar[int]
    ADV_FIELD_NUMBER: _ClassVar[int]
    ADV_MISSING_FIELD_NUMBER: _ClassVar[int]
    ADV_STALE_FIELD_NUMBER: _ClassVar[int]
    LVAR_CONTRIBUTION_FIELD_NUMBER: _ClassVar[int]
    STRESSED_LIQUIDATION_VALUE_FIELD_NUMBER: _ClassVar[int]
    CONCENTRATION_STATUS_FIELD_NUMBER: _ClassVar[int]
    instrument_id: str
    asset_class: _types_pb2.AssetClass
    market_value: float
    tier: LiquidityTier
    horizon_days: int
    adv: float
    adv_missing: bool
    adv_stale: bool
    lvar_contribution: float
    stressed_liquidation_value: float
    concentration_status: str
    def __init__(self, instrument_id: _Optional[str] = ..., asset_class: _Optional[_Union[_types_pb2.AssetClass, str]] = ..., market_value: _Optional[float] = ..., tier: _Optional[_Union[LiquidityTier, str]] = ..., horizon_days: _Optional[int] = ..., adv: _Optional[float] = ..., adv_missing: bool = ..., adv_stale: bool = ..., lvar_contribution: _Optional[float] = ..., stressed_liquidation_value: _Optional[float] = ..., concentration_status: _Optional[str] = ...) -> None: ...

class LiquidityAdjustedVaRResponse(_message.Message):
    __slots__ = ("book_id", "portfolio_lvar", "data_completeness", "position_risks", "portfolio_concentration_status", "calculated_at", "var_1day", "lvar_ratio", "weighted_avg_horizon", "max_horizon", "concentration_count", "adv_data_as_of")
    BOOK_ID_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_LVAR_FIELD_NUMBER: _ClassVar[int]
    DATA_COMPLETENESS_FIELD_NUMBER: _ClassVar[int]
    POSITION_RISKS_FIELD_NUMBER: _ClassVar[int]
    PORTFOLIO_CONCENTRATION_STATUS_FIELD_NUMBER: _ClassVar[int]
    CALCULATED_AT_FIELD_NUMBER: _ClassVar[int]
    VAR_1DAY_FIELD_NUMBER: _ClassVar[int]
    LVAR_RATIO_FIELD_NUMBER: _ClassVar[int]
    WEIGHTED_AVG_HORIZON_FIELD_NUMBER: _ClassVar[int]
    MAX_HORIZON_FIELD_NUMBER: _ClassVar[int]
    CONCENTRATION_COUNT_FIELD_NUMBER: _ClassVar[int]
    ADV_DATA_AS_OF_FIELD_NUMBER: _ClassVar[int]
    book_id: _types_pb2.BookId
    portfolio_lvar: float
    data_completeness: float
    position_risks: _containers.RepeatedCompositeFieldContainer[PositionLiquidityRisk]
    portfolio_concentration_status: str
    calculated_at: _timestamp_pb2.Timestamp
    var_1day: float
    lvar_ratio: float
    weighted_avg_horizon: float
    max_horizon: float
    concentration_count: int
    adv_data_as_of: str
    def __init__(self, book_id: _Optional[_Union[_types_pb2.BookId, _Mapping]] = ..., portfolio_lvar: _Optional[float] = ..., data_completeness: _Optional[float] = ..., position_risks: _Optional[_Iterable[_Union[PositionLiquidityRisk, _Mapping]]] = ..., portfolio_concentration_status: _Optional[str] = ..., calculated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., var_1day: _Optional[float] = ..., lvar_ratio: _Optional[float] = ..., weighted_avg_horizon: _Optional[float] = ..., max_horizon: _Optional[float] = ..., concentration_count: _Optional[int] = ..., adv_data_as_of: _Optional[str] = ...) -> None: ...
