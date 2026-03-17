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

class InstrumentTypeEnum(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    INSTRUMENT_TYPE_UNSPECIFIED: _ClassVar[InstrumentTypeEnum]
    CASH_EQUITY: _ClassVar[InstrumentTypeEnum]
    GOVERNMENT_BOND: _ClassVar[InstrumentTypeEnum]
    CORPORATE_BOND: _ClassVar[InstrumentTypeEnum]
    FX_SPOT: _ClassVar[InstrumentTypeEnum]
    FX_FORWARD: _ClassVar[InstrumentTypeEnum]
    EQUITY_OPTION: _ClassVar[InstrumentTypeEnum]
    EQUITY_FUTURE: _ClassVar[InstrumentTypeEnum]
    COMMODITY_FUTURE: _ClassVar[InstrumentTypeEnum]
    COMMODITY_OPTION: _ClassVar[InstrumentTypeEnum]
    FX_OPTION: _ClassVar[InstrumentTypeEnum]
    INTEREST_RATE_SWAP: _ClassVar[InstrumentTypeEnum]
ASSET_CLASS_UNSPECIFIED: AssetClass
EQUITY: AssetClass
FIXED_INCOME: AssetClass
FX: AssetClass
COMMODITY: AssetClass
DERIVATIVE: AssetClass
INSTRUMENT_TYPE_UNSPECIFIED: InstrumentTypeEnum
CASH_EQUITY: InstrumentTypeEnum
GOVERNMENT_BOND: InstrumentTypeEnum
CORPORATE_BOND: InstrumentTypeEnum
FX_SPOT: InstrumentTypeEnum
FX_FORWARD: InstrumentTypeEnum
EQUITY_OPTION: InstrumentTypeEnum
EQUITY_FUTURE: InstrumentTypeEnum
COMMODITY_FUTURE: InstrumentTypeEnum
COMMODITY_OPTION: InstrumentTypeEnum
FX_OPTION: InstrumentTypeEnum
INTEREST_RATE_SWAP: InstrumentTypeEnum

class Money(_message.Message):
    __slots__ = ("amount", "currency")
    AMOUNT_FIELD_NUMBER: _ClassVar[int]
    CURRENCY_FIELD_NUMBER: _ClassVar[int]
    amount: str
    currency: str
    def __init__(self, amount: _Optional[str] = ..., currency: _Optional[str] = ...) -> None: ...

class BookId(_message.Message):
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

class OptionAttributes(_message.Message):
    __slots__ = ("underlying_id", "option_type", "strike", "expiry_date", "exercise_style", "contract_multiplier", "dividend_yield")
    UNDERLYING_ID_FIELD_NUMBER: _ClassVar[int]
    OPTION_TYPE_FIELD_NUMBER: _ClassVar[int]
    STRIKE_FIELD_NUMBER: _ClassVar[int]
    EXPIRY_DATE_FIELD_NUMBER: _ClassVar[int]
    EXERCISE_STYLE_FIELD_NUMBER: _ClassVar[int]
    CONTRACT_MULTIPLIER_FIELD_NUMBER: _ClassVar[int]
    DIVIDEND_YIELD_FIELD_NUMBER: _ClassVar[int]
    underlying_id: str
    option_type: str
    strike: float
    expiry_date: str
    exercise_style: str
    contract_multiplier: float
    dividend_yield: float
    def __init__(self, underlying_id: _Optional[str] = ..., option_type: _Optional[str] = ..., strike: _Optional[float] = ..., expiry_date: _Optional[str] = ..., exercise_style: _Optional[str] = ..., contract_multiplier: _Optional[float] = ..., dividend_yield: _Optional[float] = ...) -> None: ...

class BondAttributes(_message.Message):
    __slots__ = ("face_value", "coupon_rate", "coupon_frequency", "maturity_date", "issuer", "credit_rating", "seniority", "day_count_convention")
    FACE_VALUE_FIELD_NUMBER: _ClassVar[int]
    COUPON_RATE_FIELD_NUMBER: _ClassVar[int]
    COUPON_FREQUENCY_FIELD_NUMBER: _ClassVar[int]
    MATURITY_DATE_FIELD_NUMBER: _ClassVar[int]
    ISSUER_FIELD_NUMBER: _ClassVar[int]
    CREDIT_RATING_FIELD_NUMBER: _ClassVar[int]
    SENIORITY_FIELD_NUMBER: _ClassVar[int]
    DAY_COUNT_CONVENTION_FIELD_NUMBER: _ClassVar[int]
    face_value: float
    coupon_rate: float
    coupon_frequency: int
    maturity_date: str
    issuer: str
    credit_rating: str
    seniority: str
    day_count_convention: str
    def __init__(self, face_value: _Optional[float] = ..., coupon_rate: _Optional[float] = ..., coupon_frequency: _Optional[int] = ..., maturity_date: _Optional[str] = ..., issuer: _Optional[str] = ..., credit_rating: _Optional[str] = ..., seniority: _Optional[str] = ..., day_count_convention: _Optional[str] = ...) -> None: ...

class FutureAttributes(_message.Message):
    __slots__ = ("underlying_id", "expiry_date", "contract_size")
    UNDERLYING_ID_FIELD_NUMBER: _ClassVar[int]
    EXPIRY_DATE_FIELD_NUMBER: _ClassVar[int]
    CONTRACT_SIZE_FIELD_NUMBER: _ClassVar[int]
    underlying_id: str
    expiry_date: str
    contract_size: float
    def __init__(self, underlying_id: _Optional[str] = ..., expiry_date: _Optional[str] = ..., contract_size: _Optional[float] = ...) -> None: ...

class FxAttributes(_message.Message):
    __slots__ = ("base_currency", "quote_currency", "delivery_date", "forward_rate")
    BASE_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    QUOTE_CURRENCY_FIELD_NUMBER: _ClassVar[int]
    DELIVERY_DATE_FIELD_NUMBER: _ClassVar[int]
    FORWARD_RATE_FIELD_NUMBER: _ClassVar[int]
    base_currency: str
    quote_currency: str
    delivery_date: str
    forward_rate: float
    def __init__(self, base_currency: _Optional[str] = ..., quote_currency: _Optional[str] = ..., delivery_date: _Optional[str] = ..., forward_rate: _Optional[float] = ...) -> None: ...

class SwapAttributes(_message.Message):
    __slots__ = ("notional", "fixed_rate", "float_index", "float_spread", "effective_date", "maturity_date", "pay_receive", "fixed_frequency", "float_frequency", "day_count_convention")
    NOTIONAL_FIELD_NUMBER: _ClassVar[int]
    FIXED_RATE_FIELD_NUMBER: _ClassVar[int]
    FLOAT_INDEX_FIELD_NUMBER: _ClassVar[int]
    FLOAT_SPREAD_FIELD_NUMBER: _ClassVar[int]
    EFFECTIVE_DATE_FIELD_NUMBER: _ClassVar[int]
    MATURITY_DATE_FIELD_NUMBER: _ClassVar[int]
    PAY_RECEIVE_FIELD_NUMBER: _ClassVar[int]
    FIXED_FREQUENCY_FIELD_NUMBER: _ClassVar[int]
    FLOAT_FREQUENCY_FIELD_NUMBER: _ClassVar[int]
    DAY_COUNT_CONVENTION_FIELD_NUMBER: _ClassVar[int]
    notional: float
    fixed_rate: float
    float_index: str
    float_spread: float
    effective_date: str
    maturity_date: str
    pay_receive: str
    fixed_frequency: int
    float_frequency: int
    day_count_convention: str
    def __init__(self, notional: _Optional[float] = ..., fixed_rate: _Optional[float] = ..., float_index: _Optional[str] = ..., float_spread: _Optional[float] = ..., effective_date: _Optional[str] = ..., maturity_date: _Optional[str] = ..., pay_receive: _Optional[str] = ..., fixed_frequency: _Optional[int] = ..., float_frequency: _Optional[int] = ..., day_count_convention: _Optional[str] = ...) -> None: ...

class Position(_message.Message):
    __slots__ = ("book_id", "instrument_id", "asset_class", "quantity", "market_value", "unrealized_pnl", "as_of", "instrument_type", "option_attrs", "bond_attrs", "future_attrs", "fx_attrs", "swap_attrs")
    BOOK_ID_FIELD_NUMBER: _ClassVar[int]
    INSTRUMENT_ID_FIELD_NUMBER: _ClassVar[int]
    ASSET_CLASS_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    MARKET_VALUE_FIELD_NUMBER: _ClassVar[int]
    UNREALIZED_PNL_FIELD_NUMBER: _ClassVar[int]
    AS_OF_FIELD_NUMBER: _ClassVar[int]
    INSTRUMENT_TYPE_FIELD_NUMBER: _ClassVar[int]
    OPTION_ATTRS_FIELD_NUMBER: _ClassVar[int]
    BOND_ATTRS_FIELD_NUMBER: _ClassVar[int]
    FUTURE_ATTRS_FIELD_NUMBER: _ClassVar[int]
    FX_ATTRS_FIELD_NUMBER: _ClassVar[int]
    SWAP_ATTRS_FIELD_NUMBER: _ClassVar[int]
    book_id: BookId
    instrument_id: InstrumentId
    asset_class: AssetClass
    quantity: float
    market_value: Money
    unrealized_pnl: Money
    as_of: _timestamp_pb2.Timestamp
    instrument_type: InstrumentTypeEnum
    option_attrs: OptionAttributes
    bond_attrs: BondAttributes
    future_attrs: FutureAttributes
    fx_attrs: FxAttributes
    swap_attrs: SwapAttributes
    def __init__(self, book_id: _Optional[_Union[BookId, _Mapping]] = ..., instrument_id: _Optional[_Union[InstrumentId, _Mapping]] = ..., asset_class: _Optional[_Union[AssetClass, str]] = ..., quantity: _Optional[float] = ..., market_value: _Optional[_Union[Money, _Mapping]] = ..., unrealized_pnl: _Optional[_Union[Money, _Mapping]] = ..., as_of: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ..., instrument_type: _Optional[_Union[InstrumentTypeEnum, str]] = ..., option_attrs: _Optional[_Union[OptionAttributes, _Mapping]] = ..., bond_attrs: _Optional[_Union[BondAttributes, _Mapping]] = ..., future_attrs: _Optional[_Union[FutureAttributes, _Mapping]] = ..., fx_attrs: _Optional[_Union[FxAttributes, _Mapping]] = ..., swap_attrs: _Optional[_Union[SwapAttributes, _Mapping]] = ...) -> None: ...
