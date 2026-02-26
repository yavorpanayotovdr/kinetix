import datetime

from google.protobuf import timestamp_pb2 as _timestamp_pb2
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class AssetClass(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ASSET_CLASS_UNSPECIFIED: _ClassVar[AssetClass]
    EQUITY: _ClassVar[AssetClass]
    FIXED_INCOME: _ClassVar[AssetClass]
    FX: _ClassVar[AssetClass]
    COMMODITY: _ClassVar[AssetClass]
    DERIVATIVE: _ClassVar[AssetClass]
ASSET_CLASS_UNSPECIFIED: AssetClass
EQUITY: AssetClass
FIXED_INCOME: AssetClass
FX: AssetClass
COMMODITY: AssetClass
DERIVATIVE: AssetClass

class Money(_message.Message):
    __slots__ = ("amount", "currency")
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    amount: str
    currency: str
    def __init__(self, amount: _Optional[str] = ..., currency: _Optional[str] = ...) -> None: ...

class PortfolioId(_message.Message):
    __slots__ = ("value",)
    VALUE_FIELD_NUMBER: _ClassVar[int]
    value: str
    def __init__(self, value: _Optional[str] = ...) -> None: ...

class TradeId(_message.Message):
    __slots__ = ("value",)
    VALUE_FIELD_NUMBER: _ClassVar[int]
    value: str
    def __init__(self, value: _Optional[str] = ...) -> None: ...

class InstrumentId(_message.Message):
    __slots__ = ("value",)
    VALUE_FIELD_NUMBER: _ClassVar[int]
    value: str
    def __init__(self, value: _Optional[str] = ...) -> None: ...

class Position(_message.Message):
    __slots__ = ("portfolio_id", "instrument_id", "asset_class", "quantity", "market_value", "unrealized_pnl", "as_of")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    MARKET_VALUE_FIELD_NUMBER: _ClassVar[int]
    UNREALIZED_PNL_FIELD_NUMBER: _ClassVar[int]
    AS_OF_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: PortfolioId
    instrument_id: InstrumentId
    asset_class: AssetClass
    quantity: float
    market_value: Money
    unrealized_pnl: Money
    as_of: _timestamp_pb2.Timestamp
    def __init__(self, portfolio_id: _Optional[_Union[PortfolioId, _Mapping]] = ..., instrument_id: _Optional[_Union[InstrumentId, _Mapping]] = ..., asset_class: _Optional[_Union[AssetClass, str]] = ..., quantity: _Optional[float] = ..., market_value: _Optional[_Union[Money, _Mapping]] = ..., unrealized_pnl: _Optional[_Union[Money, _Mapping]] = ..., as_of: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
