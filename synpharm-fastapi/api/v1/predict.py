import logging
from fastapi import APIRouter

from core.schemas import (
    SingleRequest, BatchPredictionRequest,
    AlgoResponse, BatchPredictionResponse
)
from services.dti_service import DTIService
from services.ppi_service import PPIService
from services.ddi_service import DDIService
from services.batch_service import BatchPredictor
from services.algorithm_adapters import get_ddi_drug_list
from core.exceptions import PredictionError, InvalidInputError, ModelNotFoundError
from config import settings

logger = logging.getLogger(__name__)

router = APIRouter()

dti_engine = DTIService()
ppi_engine = PPIService()
ddi_engine = DDIService()
batch_predictor = BatchPredictor()


@router.post("/single", response_model=AlgoResponse)
async def predict_single(req: SingleRequest):
    logger.info("Single prediction request: algo_type=%s", req.algo_type)

    try:
        result = _dispatch(req.algo_type, req)
    except PredictionError:
        raise
    except Exception as e:
        logger.error("Prediction failed: %s", e, exc_info=True)
        raise PredictionError("预测失败: " + str(e))

    logger.info("Single prediction completed: algo_type=%s", req.algo_type)
    return {"status": "success", "metrics": result}


@router.post("/batch", response_model=BatchPredictionResponse)
async def predict_batch(req: BatchPredictionRequest):
    logger.info("Batch prediction request: algo_type=%s, size=%d", req.algo_type, len(req.data_list))

    if len(req.data_list) > settings.max_batch_size:
        raise InvalidInputError(f"批量大小超过限制，最大{settings.max_batch_size}条")

    try:
        data_list = [item.model_dump() for item in req.data_list]
        results = batch_predictor.run(data_list, req.algo_type)
    except PredictionError:
        raise
    except Exception as e:
        logger.error("Batch prediction failed: %s", e, exc_info=True)
        raise PredictionError("批量预测失败: " + str(e))

    success_count = sum(1 for r in results if r["status"] == "success")
    fail_count = len(results) - success_count

    logger.info("Batch prediction completed: algo_type=%s, success=%d, fail=%d",
                req.algo_type, success_count, fail_count)
    return {
        "status": "success",
        "total": len(results),
        "success_count": success_count,
        "fail_count": fail_count,
        "results": results,
    }


@router.get("/ddi/drugs")
async def get_ddi_drugs():
    """返回 DDI 训练图内药物（DrugBank ID）列表，供前端下拉选择。"""
    drugs = get_ddi_drug_list()
    if drugs is None:
        raise ModelNotFoundError("DDI")
    return {"status": "success", "drugs": drugs, "count": len(drugs)}


def _dispatch(algo_type: str, req: SingleRequest):
    if algo_type == "DTI":
        return dti_engine.predict({"drug_smiles": req.drug_smiles, "target_seq": req.target_seq})
    if algo_type == "PPI":
        return ppi_engine.predict({"protein_a": req.protein_a, "protein_b": req.protein_b})
    if algo_type == "DDI":
        return ddi_engine.predict({"drug_a": req.drug_a, "drug_b": req.drug_b})
    raise InvalidInputError(f"未知算法类型: {algo_type}")
