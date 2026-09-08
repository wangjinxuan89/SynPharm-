"""输入校验：蛋白质序列 / SMILES 的标准化与防御性校验。

FastAPI 只接收 Java 标准化后的输入，这里做最后一道防御：
在进入模型推理前拦截非法内容，返回明确错误码
（INVALID_SEQUENCE / SEQUENCE_TOO_SHORT / INVALID_SMILES）。
"""
from __future__ import annotations

from core.exceptions import (
    InvalidSequenceError,
    SequenceTooShortError,
    InvalidSmilesError,
)

# 标准 20 种氨基酸（大写）
ALLOWED_AA = frozenset("ACDEFGHIKLMNPQRSTVWY")
# 与 KAN-MoDTI 的 QSO 特征一致：序列至少需要 31 个残基
MIN_SEQ_LENGTH = 31


def normalize_sequence(seq: str) -> str:
    """去空白 + 转大写。"""
    return "".join(seq.split()).upper()


def validate_sequence(seq: str, field: str = "target_seq") -> str:
    """校验并返回标准化序列。

    非法字符抛 ``INVALID_SEQUENCE``，长度不足抛 ``SEQUENCE_TOO_SHORT``。
    """
    if not seq or not seq.strip():
        raise InvalidSequenceError("蛋白质序列不能为空", field=field)

    seq = normalize_sequence(seq)
    illegal = sorted({c for c in seq if c not in ALLOWED_AA})
    if illegal:
        raise InvalidSequenceError(
            f"蛋白质序列包含非法字符: {''.join(illegal)}", field=field)

    if len(seq) < MIN_SEQ_LENGTH:
        raise SequenceTooShortError(
            f"蛋白质序列长度不足，至少需要 {MIN_SEQ_LENGTH} 个残基（当前 {len(seq)}）",
            field=field)
    return seq


def validate_smiles(smiles: str, field: str = "drug_smiles") -> str:
    """SMILES 非空校验；RDKit 可用时顺带校验分子结构（否则仅非空）。"""
    if not smiles or not smiles.strip():
        raise InvalidSmilesError("SMILES 不能为空", field=field)

    smiles = smiles.strip()

    try:
        from rdkit import Chem
    except ImportError:
        # rdkit 未安装时不阻断，仅做非空校验（深度校验交给算法层）
        return smiles

    if Chem.MolFromSmiles(smiles) is None:
        raise InvalidSmilesError(f"无法解析的 SMILES: {smiles}", field=field)
    return smiles
