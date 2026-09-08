from fastapi import Request
from config import settings
from core.exceptions import AuthError

# 请求头中携带 API Key 的名称
API_KEY_HEADER = "X-API-Key"


async def verify_api_key(request: Request):
    """校验 API Key（从请求头 X-API-Key 读取）。

    未配置 API_KEYS（api_key_list 为空）时跳过校验，仅限内网开发。
    校验失败抛 ``AuthError``（HTTP 401，统一错误体 ``{code, message}``）。
    """
    valid_keys = settings.api_key_list
    if not valid_keys:
        return

    api_key = request.headers.get(API_KEY_HEADER)
    if api_key not in valid_keys:
        raise AuthError("Unauthorized: Invalid API Key")
