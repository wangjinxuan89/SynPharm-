# SynPharm FastAPI 后端对齐实现汇报

> 依据：《未实现功能修复技术方案》第 6 节（Python FastAPI 后端）。
> 范围：本次完成 6.1 ~ 6.6 全部功能点，并补齐对应验收标准（第 8 节）与测试（第 9 节 Python 部分）。

---

## 一、改动总览

| 方案条目 | 内容 | 状态 | 涉及文件 |
|:---|:---|:---:|:---|
| 6.1 | 保持输入职责清晰：只接收标准序列 / SMILES | ✅ | `core/schemas.py`、各 service |
| 6.2 | 序列校验：去空白、转大写、最小长度 31、非法字符、必填 | ✅ | `core/validation.py`（新增）、各 service |
| 6.3 | 统一错误响应 `{code,message,details}` + 状态码语义 | ✅ | `core/exceptions.py`、`core/auth.py` |
| 6.4 | interactions 不伪造；binding_affinity 显式标记不可用 | ✅ | `core/schemas.py`、各 service |
| 6.5 | 模型懒加载加锁 + 失败/就绪状态；`/health/` 区分存活与就绪 | ✅ | `services/algorithm_adapters.py`、`api/v1/health.py` |
| 6.6 | 批量接口逐行返回成功/失败与错误码 | ✅ | `services/batch_service.py`、`api/v1/predict.py` |

---

## 二、逐项说明

### 6.1 输入职责清晰
FastAPI 只负责「接收标准化输入 → 校验 → 推理」，不承担 UniProt/PDB ID 解析（该职责在 Java 侧）。

- DTI：`algo_type + drug_smiles + target_seq`
- PPI：`algo_type + protein_a + protein_b`
- DDI：`algo_type + drug_a + drug_b`

### 6.2 序列校验（新增 `core/validation.py`）
- 去空白、转大写（`normalize_sequence`）
- 允许字符：`ACDEFGHIKLMNPQRSTVWY`（20 标准氨基酸）
- 最小长度 31 残基（与 KAN-MoDTI 的 QSO 特征 `MAXLAG+1` 一致）
- 非法字符 → `INVALID_SEQUENCE`（400）；长度不足 → `SEQUENCE_TOO_SHORT`（400）
- SMILES 空 → `INVALID_SMILES`（400）；RDKit 可用时顺带校验分子可解析

### 6.3 统一错误响应（重写 `core/exceptions.py`）
错误体统一为 `{code, message, details?}`，状态码语义如下：

| 状态码 | 场景 | code |
|:---:|:---|:---|
| 422 | 请求结构错误 | `VALIDATION_ERROR` |
| 400 | 输入内容非法 | `INVALID_SEQUENCE` / `SEQUENCE_TOO_SHORT` / `INVALID_SMILES` / `INVALID_INPUT` |
| 401 | API Key 错误 | `UNAUTHORIZED` |
| 503 | 模型权重/依赖不可用 | `MODEL_UNAVAILABLE` |
| 500 | 未预期异常 | `INTERNAL_ERROR` |

关键变更：模型缺失从原 404 调整为 **503**（`MODEL_UNAVAILABLE`），与「算法引擎故障」语义一致。

示例：
```json
{ "code": "SEQUENCE_TOO_SHORT", "message": "蛋白质序列长度不足，至少需要 31 个残基（当前 3）", "details": { "field": "target_seq" } }
```

### 6.4 interactions / binding affinity 能力标记
- `interactions` 保持 `[]`，新增 `explanation_available: false` + `explanation_message`
- `binding_affinity` 二分类模型保持 `null`，新增 `binding_affinity_available: false`
- 前端据 `confidence_score` 区分，不再把置信度当亲和力

### 6.5 模型健康检查（`services/algorithm_adapters.py` + `api/v1/health.py`）
- 懒加载增加 **线程锁**（防并发重复加载）、**双重检查**、**失败缓存**
- 新增状态跟踪：`unloaded / loading / ready / unavailable`
- `/health/` 返回：
```json
{
  "status": "ready",
  "service": "SynPharm AI Prediction Engine",
  "models": { "DTI": "ready", "PPI": "ready", "DDI": "ready" }
}
```
`status` 为 `ready / degraded / loading / not_ready`，明确区分「服务存活」与「模型是否真正可推理」。

### 6.6 批量接口逐行结果
响应结构：
```json
{
  "status": "success",
  "total": 2,
  "success_count": 1,
  "fail_count": 1,
  "results": [
    { "row_number": 1, "status": "success", "metrics": { "confidence_score": 0.98, "confidence_level": "high" } },
    { "row_number": 2, "status": "failed", "error_code": "SEQUENCE_TOO_SHORT", "error_message": "蛋白质序列长度不足…" }
  ]
}
```

---

## 三、验收标准对照（第 8 节 Python 部分）

| 验收项 | 达成 |
|:---|:---:|
| DTI/PPI 接收有效蛋白质序列 | ✅ |
| 非法序列返回明确错误码 | ✅ |
| 模型不可用返回 503 | ✅ |
| interactions 不返回伪造数据 | ✅ |
| binding affinity 不可用时有明确标记 | ✅ |
| 批量接口逐条返回成功/失败原因 | ✅ |
| `/health/` 区分服务状态和模型状态 | ✅ |

---

## 四、测试

- 新增 `tests/test_validation.py`，覆盖：序列标准化、非法字符、长度不足、SMILES 空、503 错误体、服务层缺失序列返回 400、批量逐行状态。
- 运行：`cd synpharm-fastapi && pytest tests/ -q`

### 实测结果（2026-09-08）

```
7 passed in 2.83s
```

| 用例 | 结果 |
|:---|:---:|
| test_normalize_sequence（序列标准化） | ✅ |
| test_invalid_sequence_chars（非法字符 → INVALID_SEQUENCE） | ✅ |
| test_sequence_too_short（长度不足 → SEQUENCE_TOO_SHORT） | ✅ |
| test_empty_smiles（SMILES 空 → INVALID_SMILES） | ✅ |
| test_model_unavailable_is_503（模型缺失 → 503） | ✅ |
| test_dti_missing_sequence_returns_400（缺序列 → 400） | ✅ |
| test_batch_per_row_status（批量逐行状态/错误码） | ✅ |

- 环境：Python 3.10.9 / fastapi 0.104.1 / pydantic 2.5.0 / torch 2.13.0+cu126 / pytest 9.1.1
- 7 个用例全部真实执行（torch 已装，`importorskip` 未触发跳过）；服务层用例以非法输入在加载模型前拦截，无权重加载耗时。

---

## 五、遗留事项与风险提示（需跨端协调，非本次 FastAPI 改动范围）

1. **DDI 输入契约冲突**：方案 4.2 写「DDI 输入为两个药物 SMILES」，但 DDI-LLM 模型实际接收的是 **DrugBank ID**（`node_id_map` 键）。当前 FastAPI 对 DDI 只做非空校验并透传，需与前端/Java 确认统一为 DrugBank ID 还是 SMILES。
2. **非标准氨基酸残基**：序列校验仅允许 20 标准氨基酸。UniProt 真实序列可能含 `X / B / Z / U` 等，需 Java 侧在解析时清洗，否则会被判为 `INVALID_SEQUENCE`。
3. **解析前置依赖**：UniProt/PDB ID → 序列的解析在 Java 侧（方案 5.3），FastAPI 只收标准序列；联调前需 Java 完成 `PredictionInputResolver`。
4. **懒加载语义**：模型首次预测前 `/health/` 显示 `not_ready`（模型为 `unloaded`），属预期行为；可通过触发一次预测或后续增加启动预热来转为 `ready`。
