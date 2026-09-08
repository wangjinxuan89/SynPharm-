from core.schemas import PredictionMetrics
from core.base_algo import BaseAlgo
from core.exceptions import InvalidSmilesError, ModelNotFoundError
from core.validation import validate_smiles, validate_sequence
from services.algorithm_adapters import get_dti_predictor


class DTIService(BaseAlgo):

    def __init__(self):
        self._predictor = None
        self._loaded = False

    def _get_predictor(self):
        if not self._loaded:
            self._predictor = get_dti_predictor()
            self._loaded = True
        return self._predictor

    def predict(self, data: dict) -> PredictionMetrics:
        smiles = data.get("drug_smiles") or ""
        target_seq = data.get("target_seq") or ""

        # 防御性校验 + 标准化：进入模型前拦截非法输入，返回明确错误码
        smiles = validate_smiles(smiles, field="drug_smiles")
        target_seq = validate_sequence(target_seq, field="target_seq")

        predictor = self._get_predictor()
        if predictor is None:
            # 权重/依赖缺失：模型不可用（503），不再返回 mock
            raise ModelNotFoundError("DTI")

        # 真实推理：序列已通过长度/字符校验，剩余 ValueError 均为 SMILES 相关
        try:
            pred, score = predictor.predict(smiles, target_seq)
        except ValueError as e:
            raise InvalidSmilesError(str(e), field="drug_smiles")
        return self._to_metrics(pred, score)

    def _to_metrics(self, pred: int, score: float) -> PredictionMetrics:
        # KAN-MoDTI 是二分类，无回归结合亲和力：binding_affinity 置空并显式标记不可用
        return PredictionMetrics(
            target_id="P00533",
            target_name="EGFR",
            binding_affinity=None,
            binding_affinity_available=False,
            confidence_score=round(score, 4),
            confidence_level=_confidence_level(score),
            interactions=[],
            explanation_available=False,
            explanation_message="当前模型仅提供整体预测分数",
        )


def _confidence_level(score: float) -> str:
    if score >= 0.8:
        return "high"
    if score >= 0.6:
        return "medium"
    return "low"
