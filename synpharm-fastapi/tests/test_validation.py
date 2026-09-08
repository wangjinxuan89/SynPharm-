"""对应《未实现功能修复技术方案》第 9 节 Python 测试要求。

纯校验测试无 torch / 权重依赖；涉及服务层的测试用 importorskip 保护。
运行：``pytest tests/ -q``
"""
import pytest

from core.exceptions import (
    InvalidSequenceError, SequenceTooShortError, InvalidSmilesError,
    ModelNotFoundError, ErrorCode,
)
from core.validation import validate_sequence, validate_smiles, MIN_SEQ_LENGTH

VALID_SEQ = "MKTIIALSYIFCLVFADYKDDDDK" * 2  # 52 残基


# ---- 序列 / SMILES 校验 ----

def test_normalize_sequence():
    assert validate_sequence("  " + VALID_SEQ.lower() + "\n", "target_seq") == VALID_SEQ


def test_invalid_sequence_chars():
    with pytest.raises(InvalidSequenceError) as e:
        validate_sequence("MKT" + "X" * 40, "target_seq")
    assert e.value.code == ErrorCode.INVALID_SEQUENCE
    assert e.value.status_code == 400


def test_sequence_too_short():
    with pytest.raises(SequenceTooShortError) as e:
        validate_sequence("MKT", "target_seq")
    assert e.value.code == ErrorCode.SEQUENCE_TOO_SHORT


def test_empty_smiles():
    with pytest.raises(InvalidSmilesError) as e:
        validate_smiles("   ", "drug_smiles")
    assert e.value.code == ErrorCode.INVALID_SMILES


def test_model_unavailable_is_503():
    e = ModelNotFoundError("DTI")
    assert e.status_code == 503
    assert e.code == ErrorCode.MODEL_UNAVAILABLE
    assert e.to_dict() == {"code": "MODEL_UNAVAILABLE", "message": "模型不可用: DTI"}


# ---- 服务层（需 torch，缺失时跳过）----

def test_dti_missing_sequence_returns_400():
    pytest.importorskip("torch")
    from services.dti_service import DTIService
    with pytest.raises(InvalidSequenceError) as e:
        DTIService().predict({"drug_smiles": "CCO", "target_seq": ""})
    assert e.value.status_code == 400


def test_batch_per_row_status():
    pytest.importorskip("torch")
    from services.batch_service import BatchPredictor
    results = BatchPredictor().run(
        [
            {"drug_smiles": "CCO", "target_seq": ""},
            {"drug_smiles": "CCO", "target_seq": "MKT"},
        ],
        "DTI",
    )
    assert [r["status"] for r in results] == ["failed", "failed"]
    assert results[0]["error_code"] == ErrorCode.INVALID_SEQUENCE
    assert results[1]["error_code"] == ErrorCode.SEQUENCE_TOO_SHORT
    assert results[0]["row_number"] == 1 and results[1]["row_number"] == 2
