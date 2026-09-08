from typing import List
import logging
from core.exceptions import PredictionError, InvalidInputError, ErrorCode
from services.dti_service import DTIService
from services.ppi_service import PPIService
from services.ddi_service import DDIService

logger = logging.getLogger(__name__)


class BatchPredictor:

    def __init__(self):
        self.dti_engine = DTIService()
        self.ppi_engine = PPIService()
        self.ddi_engine = DDIService()

    def run(self, data_list: List[dict], algo_type: str) -> List[dict]:
        """逐行预测，返回结构化结果（成功带 metrics，失败带 error_code/error_message）。"""
        results = []
        engine = self._get_engine(algo_type)

        for i, item in enumerate(data_list):
            row_number = i + 1
            try:
                result = engine.predict(item)
                results.append({
                    "row_number": row_number,
                    "status": "success",
                    "metrics": result.model_dump(),
                })
            except PredictionError as e:
                logger.warning("批量预测第 %d 行失败: [%s] %s", row_number, e.code, e.message)
                results.append({
                    "row_number": row_number,
                    "status": "failed",
                    "error_code": e.code,
                    "error_message": e.message,
                })
            except Exception as e:  # noqa: BLE001 —— 单行兜底，不影响后续行
                logger.warning("批量预测第 %d 行异常: %s", row_number, e)
                results.append({
                    "row_number": row_number,
                    "status": "failed",
                    "error_code": ErrorCode.INTERNAL_ERROR,
                    "error_message": str(e),
                })

        return results

    def _get_engine(self, algo_type: str):
        if algo_type == "DTI":
            return self.dti_engine
        elif algo_type == "PPI":
            return self.ppi_engine
        elif algo_type == "DDI":
            return self.ddi_engine
        else:
            raise InvalidInputError(f"未知算法类型: {algo_type}")
