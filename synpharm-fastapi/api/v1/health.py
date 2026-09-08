from fastapi import APIRouter
from services.algorithm_adapters import get_model_status

router = APIRouter()


@router.get("/")
async def health_check():
    """服务存活 + 模型就绪状态。

    与纯存活探针区分：``status`` 反映三算法模型是否真正可推理
    （ready / degraded / loading / not_ready），``models`` 逐算法给出
    ready / loading / unavailable / unloaded。避免服务存活但实际无法预测。
    """
    models = get_model_status()
    states = list(models.values())

    if all(s == "ready" for s in states):
        overall = "ready"
    elif any(s == "loading" for s in states):
        overall = "loading"
    elif any(s == "ready" for s in states):
        overall = "degraded"
    else:
        overall = "not_ready"

    return {
        "status": overall,
        "service": "SynPharm AI Prediction Engine",
        "models": models,
    }
