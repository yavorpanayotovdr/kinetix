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

class FrtbRiskClass(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    FRTB_RISK_CLASS_UNSPECIFIED: _ClassVar[FrtbRiskClass]
    GIRR: _ClassVar[FrtbRiskClass]
    CSR_NON_SEC: _ClassVar[FrtbRiskClass]
    CSR_SEC_CTP: _ClassVar[FrtbRiskClass]
    CSR_SEC_NON_CTP: _ClassVar[FrtbRiskClass]
    FRTB_EQUITY: _ClassVar[FrtbRiskClass]
    FRTB_COMMODITY: _ClassVar[FrtbRiskClass]
    FRTB_FX: _ClassVar[FrtbRiskClass]

class ReportFormat(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    REPORT_FORMAT_UNSPECIFIED: _ClassVar[ReportFormat]
    CSV: _ClassVar[ReportFormat]
    XBRL: _ClassVar[ReportFormat]
FRTB_RISK_CLASS_UNSPECIFIED: FrtbRiskClass
GIRR: FrtbRiskClass
CSR_NON_SEC: FrtbRiskClass
CSR_SEC_CTP: FrtbRiskClass
CSR_SEC_NON_CTP: FrtbRiskClass
FRTB_EQUITY: FrtbRiskClass
FRTB_COMMODITY: FrtbRiskClass
FRTB_FX: FrtbRiskClass
REPORT_FORMAT_UNSPECIFIED: ReportFormat
CSV: ReportFormat
XBRL: ReportFormat

class FrtbRequest(_message.Message):
    __slots__ = ("portfolio_id", "positions")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ...) -> None: ...

class RiskClassCharge(_message.Message):
    __slots__ = ("risk_class", "delta_charge", "vega_charge", "curvature_charge", "total_charge")
    RISK_CLASS_FIELD_NUMBER: _ClassVar[int]
    DELTA_CHARGE_FIELD_NUMBER: _ClassVar[int]
    VEGA_CHARGE_FIELD_NUMBER: _ClassVar[int]
    CURVATURE_CHARGE_FIELD_NUMBER: _ClassVar[int]
    TOTAL_CHARGE_FIELD_NUMBER: _ClassVar[int]
    risk_class: FrtbRiskClass
    delta_charge: float
    vega_charge: float
    curvature_charge: float
    total_charge: float
    def __init__(self, risk_class: _Optional[_Union[FrtbRiskClass, str]] = ..., delta_charge: _Optional[float] = ..., vega_charge: _Optional[float] = ..., curvature_charge: _Optional[float] = ..., total_charge: _Optional[float] = ...) -> None: ...

class SbmResult(_message.Message):
    __slots__ = ("risk_class_charges", "total_sbm_charge")
    RISK_CLASS_CHARGES_FIELD_NUMBER: _ClassVar[int]
    TOTAL_SBM_CHARGE_FIELD_NUMBER: _ClassVar[int]
    risk_class_charges: _containers.RepeatedCompositeFieldContainer[RiskClassCharge]
    total_sbm_charge: float
    def __init__(self, risk_class_charges: _Optional[_Iterable[_Union[RiskClassCharge, _Mapping]]] = ..., total_sbm_charge: _Optional[float] = ...) -> None: ...

class DrcResult(_message.Message):
    __slots__ = ("gross_jtd", "hedge_benefit", "net_drc")
    GROSS_JTD_FIELD_NUMBER: _ClassVar[int]
    HEDGE_BENEFIT_FIELD_NUMBER: _ClassVar[int]
    NET_DRC_FIELD_NUMBER: _ClassVar[int]
    gross_jtd: float
    hedge_benefit: float
    net_drc: float
    def __init__(self, gross_jtd: _Optional[float] = ..., hedge_benefit: _Optional[float] = ..., net_drc: _Optional[float] = ...) -> None: ...

class RraoResult(_message.Message):
    __slots__ = ("exotic_notional", "other_notional", "total_rrao")
    EXOTIC_NOTIONAL_FIELD_NUMBER: _ClassVar[int]
    OTHER_NOTIONAL_FIELD_NUMBER: _ClassVar[int]
    TOTAL_RRAO_FIELD_NUMBER: _ClassVar[int]
    exotic_notional: float
    other_notional: float
    total_rrao: float
    def __init__(self, exotic_notional: _Optional[float] = ..., other_notional: _Optional[float] = ..., total_rrao: _Optional[float] = ...) -> None: ...

class FrtbResponse(_message.Message):
    __slots__ = ("portfolio_id", "sbm", "drc", "rrao", "total_capital_charge", "calculated_at")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    SBM_FIELD_NUMBER: _ClassVar[int]
    DRC_FIELD_NUMBER: _ClassVar[int]
    RRAO_FIELD_NUMBER: _ClassVar[int]
    TOTAL_CAPITAL_CHARGE_FIELD_NUMBER: _ClassVar[int]
    CALCULATED_AT_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: str
    sbm: SbmResult
    drc: DrcResult
    rrao: RraoResult
    total_capital_charge: float
    calculated_at: _timestamp_pb2.Timestamp
    def __init__(self, portfolio_id: _Optional[str] = ..., sbm: _Optional[_Union[SbmResult, _Mapping]] = ..., drc: _Optional[_Union[DrcResult, _Mapping]] = ..., rrao: _Optional[_Union[RraoResult, _Mapping]] = ..., total_capital_charge: _Optional[float] = ..., calculated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...

class GenerateReportRequest(_message.Message):
    __slots__ = ("portfolio_id", "positions", "format")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    FORMAT_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: _types_pb2.PortfolioId
    positions: _containers.RepeatedCompositeFieldContainer[_types_pb2.Position]
    format: ReportFormat
    def __init__(self, portfolio_id: _Optional[_Union[_types_pb2.PortfolioId, _Mapping]] = ..., positions: _Optional[_Iterable[_Union[_types_pb2.Position, _Mapping]]] = ..., format: _Optional[_Union[ReportFormat, str]] = ...) -> None: ...

class GenerateReportResponse(_message.Message):
    __slots__ = ("portfolio_id", "format", "content", "generated_at")
    PORTFOLIO_ID_FIELD_NUMBER: _ClassVar[int]
    FORMAT_FIELD_NUMBER: _ClassVar[int]
    CONTENT_FIELD_NUMBER: _ClassVar[int]
    GENERATED_AT_FIELD_NUMBER: _ClassVar[int]
    portfolio_id: str
    format: ReportFormat
    content: str
    generated_at: _timestamp_pb2.Timestamp
    def __init__(self, portfolio_id: _Optional[str] = ..., format: _Optional[_Union[ReportFormat, str]] = ..., content: _Optional[str] = ..., generated_at: _Optional[_Union[datetime.datetime, _timestamp_pb2.Timestamp, _Mapping]] = ...) -> None: ...
