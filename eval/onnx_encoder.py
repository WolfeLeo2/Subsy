"""Exports a GLiNER2 checkpoint's encoder to ONNX and quantizes it to int8, the form the app would ship.

Usage: python -I eval/onnx_encoder.py <model dir> <out dir>
Writes encoder.onnx (fp32) and encoder.int8.onnx. run_eval.py takes the int8 file as a third argument and swaps it in
for the torch encoder, so the eval scores what the phone would run (the small task heads stay in torch for now).
"""

import sys
from pathlib import Path
from types import SimpleNamespace

import torch

MODEL_INPUTS = ["input_ids", "attention_mask"]


class OrtEncoder(torch.nn.Module):
    """Stands in for the DeBERTa encoder, running the exported graph through onnxruntime on CPU."""

    def __init__(self, path, threads=4):
        super().__init__()
        import onnxruntime as ort

        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        self.session = ort.InferenceSession(str(path), opts, providers=["CPUExecutionProvider"])

    def forward(self, input_ids, attention_mask):
        (hidden,) = self.session.run(None, {"input_ids": input_ids.numpy(), "attention_mask": attention_mask.numpy()})
        return SimpleNamespace(last_hidden_state=torch.from_numpy(hidden))


class _Encoder(torch.nn.Module):
    def __init__(self, encoder):
        super().__init__()
        self.encoder = encoder

    def forward(self, input_ids, attention_mask):
        return self.encoder(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state


def main(model_dir, out_dir):
    from gliner2 import AutoExtractor
    from onnxruntime.quantization import QuantType, quantize_dynamic

    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    model = AutoExtractor.from_pretrained(model_dir).eval()
    sample = (torch.ones(1, 24, dtype=torch.long), torch.ones(1, 24, dtype=torch.long))
    fp32 = out / "encoder.onnx"
    with torch.no_grad():
        torch.onnx.export(
            _Encoder(model.encoder), sample, str(fp32), input_names=MODEL_INPUTS, output_names=["last_hidden_state"],
            dynamic_axes={"input_ids": {0: "batch", 1: "seq"}, "attention_mask": {0: "batch", 1: "seq"},
                          "last_hidden_state": {0: "batch", 1: "seq"}},
            opset_version=17, dynamo=False,
        )
    int8 = out / "encoder.int8.onnx"
    # Per-channel: on 342 messages plain int8 changed the kind in 30, per-channel in 10, and never an amount.
    quantize_dynamic(str(fp32), str(int8), weight_type=QuantType.QInt8, per_channel=True)

    # Same input, both encoders: how far does int8 drift from torch?
    ids = torch.randint(5, 1000, (1, 40))
    mask = torch.ones_like(ids)
    model.eval()  # export can leave the module in training mode, and dropout would make the reference noisy
    with torch.no_grad():
        ref = model.encoder(input_ids=ids, attention_mask=mask).last_hidden_state
    for path in (fp32, int8):
        got = OrtEncoder(path)(ids, mask).last_hidden_state
        cos = torch.nn.functional.cosine_similarity(ref.flatten(), got.flatten(), dim=0).item()
        print(f"{path.name:<20}{path.stat().st_size / 1e6:7.1f} MB   cosine vs torch {cos:.5f}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
