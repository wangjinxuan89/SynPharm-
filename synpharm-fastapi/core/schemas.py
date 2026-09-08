from pydantic import BaseModel
from typing import List, Optional


class InteractionInfo(BaseModel):
    residue: str
    type: str
    distance: float


class PredictionMetrics(BaseModel):
    target_id: Optional[str] = None
    target_name: Optional[str] = None
    # 结合亲和力：二分类模型无回归值时保持 null，并显式标记不可用
    binding_affinity: Optional[float] = None
    binding_affinity_available: bool = False
    confidence_score: float
    confidence_level: str
    interactions: List[InteractionInfo] = []
    # 残基级解释：当前模型不提供时置空，不伪造
    explanation_available: bool = False
    explanation_message: Optional[str] = "当前模型仅提供整体预测分数"


class AlgoResponse(BaseModel):
    status: str
    metrics: PredictionMetrics


class SingleRequest(BaseModel):
    algo_type: str
    drug_smiles: Optional[str] = None
    target_seq: Optional[str] = None
    protein_a: Optional[str] = None
    protein_b: Optional[str] = None
    drug_a: Optional[str] = None
    drug_b: Optional[str] = None


class BatchItem(BaseModel):
    algo_type: str
    drug_smiles: Optional[str] = None
    target_seq: Optional[str] = None
    protein_a: Optional[str] = None
    protein_b: Optional[str] = None
    drug_a: Optional[str] = None
    drug_b: Optional[str] = None


class BatchPredictionRequest(BaseModel):
    data_list: List[BatchItem]
    algo_type: str


class BatchResultItem(BaseModel):
    row_number: int
    status: str  # "success" | "failed"
    metrics: Optional[dict] = None
    error_code: Optional[str] = None
    error_message: Optional[str] = None


class BatchPredictionResponse(BaseModel):
    status: str
    total: int
    success_count: int
    fail_count: int
    results: List[BatchResultItem]
