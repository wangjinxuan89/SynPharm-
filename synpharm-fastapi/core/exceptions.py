from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
import logging

logger = logging.getLogger(__name__)


class ErrorCode:
    """统一错误码（与 Java 后端对齐，见《未实现功能修复技术方案》5.5 节）。"""
    INPUT_RESOLVE_FAILED = "INPUT_RESOLVE_FAILED"
    UNIPROT_NOT_FOUND = "UNIPROT_NOT_FOUND"
    PDB_NOT_FOUND = "PDB_NOT_FOUND"
    CHAIN_NOT_FOUND = "CHAIN_NOT_FOUND"
    INVALID_SEQUENCE = "INVALID_SEQUENCE"
    SEQUENCE_TOO_SHORT = "SEQUENCE_TOO_SHORT"
    INVALID_SMILES = "INVALID_SMILES"
    INVALID_INPUT = "INVALID_INPUT"
    FASTAPI_UNAVAILABLE = "FASTAPI_UNAVAILABLE"
    MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"
    UNAUTHORIZED = "UNAUTHORIZED"
    VALIDATION_ERROR = "VALIDATION_ERROR"
    INTERNAL_ERROR = "INTERNAL_ERROR"


class PredictionError(Exception):
    """业务异常基类：携带统一错误码、消息、字段详情与 HTTP 状态码。"""

    def __init__(self, message: str, code: str = ErrorCode.INTERNAL_ERROR,
                 status_code: int = 500, details: dict = None):
        super().__init__(message)
        self.message = message
        self.code = code
        self.status_code = status_code
        self.details = details or {}

    def to_dict(self) -> dict:
        """统一错误响应体：``{code, message, details?}``。"""
        body = {"code": self.code, "message": self.message}
        if self.details:
            body["details"] = self.details
        return body


class InvalidInputError(PredictionError):
    """输入内容非法（HTTP 400）。"""
    def __init__(self, message: str, code: str = ErrorCode.INVALID_INPUT, details: dict = None):
        super().__init__(message, code=code, status_code=400, details=details)


class InvalidSequenceError(InvalidInputError):
    def __init__(self, message: str, field: str = "target_seq"):
        super().__init__(message, code=ErrorCode.INVALID_SEQUENCE, details={"field": field})


class SequenceTooShortError(InvalidInputError):
    def __init__(self, message: str, field: str = "target_seq"):
        super().__init__(message, code=ErrorCode.SEQUENCE_TOO_SHORT, details={"field": field})


class InvalidSmilesError(InvalidInputError):
    def __init__(self, message: str, field: str = "drug_smiles"):
        super().__init__(message, code=ErrorCode.INVALID_SMILES, details={"field": field})


class ModelNotFoundError(PredictionError):
    """模型权重/依赖不可用（HTTP 503，区分于输入错误）。"""
    def __init__(self, model_name: str):
        super().__init__(f"模型不可用: {model_name}",
                         code=ErrorCode.MODEL_UNAVAILABLE, status_code=503)


class AuthError(PredictionError):
    """API Key 校验失败（HTTP 401）。"""
    def __init__(self, message: str = "Unauthorized: Invalid API Key"):
        super().__init__(message, code=ErrorCode.UNAUTHORIZED, status_code=401)


def register_exception_handlers(app: FastAPI):
    @app.exception_handler(PredictionError)
    async def prediction_error_handler(request: Request, exc: PredictionError):
        logger.error("Prediction error [%s]: %s", exc.code, exc.message)
        return JSONResponse(status_code=exc.status_code, content=exc.to_dict())

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(request: Request, exc: RequestValidationError):
        details = [
            {"field": ".".join(str(loc) for loc in e["loc"]), "message": e["msg"]}
            for e in exc.errors()
        ]
        logger.warning("Validation error: %s", details)
        return JSONResponse(
            status_code=422,
            content={
                "code": ErrorCode.VALIDATION_ERROR,
                "message": "请求参数结构错误",
                "details": details,
            },
        )

    @app.exception_handler(Exception)
    async def generic_error_handler(request: Request, exc: Exception):
        logger.error("Unexpected error: %s", exc, exc_info=True)
        return JSONResponse(
            status_code=500,
            content={"code": ErrorCode.INTERNAL_ERROR, "message": "Internal server error"},
        )
