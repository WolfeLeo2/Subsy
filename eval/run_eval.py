"""Scores an on-device model on eval/labels.jsonl. Usage: python -I eval/run_eval.py <labels.jsonl> <model.gguf | gliner dir> [encoder.onnx]

Runs Qwen through llama-server, or a GLiNER2 checkpoint directory through the gliner2 package (needs its venv), on
CPU with 4 threads as a rough stand-in for a phone. Then applies the same guards the
app will: the amount's value and the currency (code or symbol) must appear in the message, references must appear in
it too, the category must be one we offered, and subscriptions are matched by name in code. Stdlib only.
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

PORT = 8091
CATEGORIES = {
    "EXPENSE": ["GROCERIES", "DINING", "TRANSPORT", "HOUSING", "MOBILE", "SHOPPING", "HEALTH", "ENTERTAINMENT",
                "TRAVEL", "EDUCATION", "GIFTS", "OTHER"],
    "INCOME": ["SALARY", "FREELANCE", "REFUNDS", "GIFTS_RECEIVED", "INVESTMENTS", "OTHER_INCOME"],
}
ALL_CATEGORIES = set(CATEGORIES["EXPENSE"] + CATEGORIES["INCOME"])
SUBSCRIPTIONS = {"sub_netflix": "Netflix", "sub_spotify": "Spotify", "sub_youtube": "YouTube Premium"}
# The account holder's name, as the app will take it from Settings. Kept out of the repo: EVAL_OWNER="..." python ...
OWNER = os.environ.get("EVAL_OWNER", "")

SYSTEM = f"""You read one bank, mobile money or card message and return JSON only.

Fields:
- kind: "expense" (money paid or sent out), "income" (money received, refunds), "transfer" (between the account holder's own accounts), or "other" (promos, OTPs, failed or pending transactions, loans offered, chats, anything that is not a completed money movement).
- amount_text: the transaction amount copied exactly as written, including its currency marker. Never the balance, fee, transaction cost or limit.
- currency: ISO 4217 code of that amount (Ksh means KES).
- merchant: who was paid or who paid. For "M-PESA CARD for account X", the merchant is inside X.
- refs: transaction reference codes in the message.
- category: for expenses one of: GROCERIES (supermarkets, food shopping), DINING (restaurants, cafes, coffee), TRANSPORT (taxis, Uber, fuel, fares), HOUSING (rent, electricity, water, home internet), MOBILE (airtime, data bundles, phone plans), SHOPPING, HEALTH, ENTERTAINMENT (streaming, TV, games, events), TRAVEL, EDUCATION, GIFTS, OTHER (people or anything unclear). For income one of: SALARY, FREELANCE, REFUNDS (refunds, reversals), GIFTS_RECEIVED, INVESTMENTS, OTHER_INCOME. null for transfer and other.

The account holder is {OWNER}. For "other", set every other field to null.

Copy amount_text character for character from the message.

Example message: "QA12BC34DE Confirmed. Ksh250.00 paid to JAVA HOUSE. on 1/10/26 at 1:00 PM.New M-PESA balance is Ksh900.00."
Example answer: {{"kind":"expense","amount_text":"Ksh250.00","currency":"KES","merchant":"JAVA HOUSE","refs":["QA12BC34DE"],"category":"DINING"}}"""


def parse_amount(text):
    """'Ksh1,100.00' -> 1100.0, '23,40 EUR' -> 23.4, '1.234,56' -> 1234.56; None if no number."""
    m = re.search(r"\d[\d.,\s]*\d|\d", text or "")
    if not m:
        return None
    s = re.sub(r"\s", "", m.group())
    if "," in s and "." in s:
        dec = "," if s.rfind(",") > s.rfind(".") else "."
        s = s.replace("." if dec == "," else ",", "").replace(dec, ".")
    elif "," in s:
        s = s.replace(",", ".") if re.search(r",\d{2}$", s) else s.replace(",", "")
    try:
        return float(s)
    except ValueError:
        return None


def squash(s):
    return re.sub(r"\s+", " ", s or "").strip().lower()


KIND_SYNONYMS = {"charge": "expense", "payment": "expense", "debit": "expense", "refund": "income", "credit": "income"}


def numbers_in(text):
    return {parse_amount(m.group()) for m in re.finditer(r"\d[\d.,]*\d|\d", text)}


CURRENCY_MARKERS = {"KES": ["ksh", "kshs"], "USD": ["$"], "GBP": ["£"], "EUR": ["€"], "INR": ["rs", "₹"], "NGN": ["₦"]}


def currency_in(text, code):
    """The code, or one of its symbols or local spellings, is in the message."""
    lowered = text.lower()
    markers = [code.lower()] + CURRENCY_MARKERS.get(code, [])
    return any(re.search(r"(?<![a-z])" + re.escape(m) + r"(?![a-z])", lowered) for m in markers)


def match_subscription(merchant):
    """Name match in code: the subscription's first word appears in the merchant."""
    m = squash(merchant).replace(" ", "")
    for sub_id, name in SUBSCRIPTIONS.items():
        if squash(name).split()[0] in m:
            return sub_id
    return None


def guard(pred, text):
    """The app's rules: drop anything the message doesn't back up."""
    kind = KIND_SYNONYMS.get(pred.get("kind"), pred.get("kind"))
    if kind not in ("expense", "income", "transfer"):
        return {"kind": "other"}
    pred["kind"] = kind
    amount = parse_amount(pred.get("amount_text"))
    if amount is None or amount not in numbers_in(text):
        return {"kind": "other", "dropped": "amount not in text"}
    currency = (pred.get("currency") or "").upper()
    if amount is None or not re.fullmatch(r"[A-Z]{3}", currency) or not currency_in(text, currency):
        return {"kind": "other", "dropped": "bad amount or currency"}
    # Money to or from the account holder (their name from Settings) is them moving their own money.
    if kind != "transfer" and OWNER and squash(OWNER) in squash(pred.get("merchant")):
        kind = "transfer"
    return {
        "kind": kind,
        "amount": amount,
        "currency": currency,
        "merchant": pred.get("merchant"),
        "refs": [r.strip() for r in pred.get("refs") or [] if isinstance(r, str) and r.strip() and r.strip() in text],
        "category": pred.get("category") if pred.get("category") in ALL_CATEGORIES else None,
        "subscription": match_subscription(pred.get("merchant")),
    }


def ask(text):
    body = {
        "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": text}],
        "temperature": 0,
        "max_tokens": 200,
        "chat_template_kwargs": {"enable_thinking": False},
    }
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}/v1/chat/completions", json.dumps(body).encode(),
                                 {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        out = json.load(r)["choices"][0]["message"]["content"]
    out = re.sub(r"<think>.*?</think>", "", out, flags=re.S)
    m = re.search(r"\{.*\}", out, re.S)
    try:
        return json.loads(m.group()) if m else {}, out
    except json.JSONDecodeError:
        return {}, out


def start_server(model):
    proc = subprocess.Popen(["llama-server", "-m", model, "--port", str(PORT), "-c", "4096", "-ngl", "0", "-t", "4",
                             "--jinja"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(120):
        try:
            urllib.request.urlopen(f"http://127.0.0.1:{PORT}/health", timeout=1)
            return proc
        except Exception:
            time.sleep(1)
    proc.kill()
    sys.exit("llama-server did not start")


GLINER_KINDS = {
    "payment or money sent": "expense",
    "money received or refund": "income",
    "transfer between own accounts": "transfer",
    "promotion, code, loan, failed transaction or chat": "other",
}
GLINER_CATEGORIES = {
    "supermarket or groceries": "GROCERIES", "restaurant, cafe or coffee": "DINING", "taxi, fuel or fares": "TRANSPORT",
    "rent, electricity, water or home internet": "HOUSING", "airtime, data bundles or phone plan": "MOBILE",
    "shopping": "SHOPPING", "health or pharmacy": "HEALTH", "streaming, TV or games": "ENTERTAINMENT",
    "travel": "TRAVEL", "education": "EDUCATION", "gift": "GIFTS", "salary": "SALARY", "refund or reversal": "REFUNDS",
    "other": "OTHER",
}


def currency_of(span, text):
    """ISO code from the amount span, else the first currency marker in the message."""
    for source in (span, text):
        lowered = source.lower()
        for code, markers in CURRENCY_MARKERS.items():
            if any(re.search(r"(?<![a-z])" + re.escape(m) + r"(?![a-z])", lowered) for m in [code.lower()] + markers):
                return code
    return None


def gliner_backend(path, onnx_encoder=None):
    import torch
    from gliner2 import AutoExtractor

    torch.set_num_threads(4)  # same budget as llama-server's -t 4
    model = AutoExtractor.from_pretrained(path)
    if onnx_encoder:
        sys.path.insert(0, str(Path(__file__).parent))  # -I leaves the script's folder off the path
        from onnx_encoder import OrtEncoder

        model.encoder = OrtEncoder(onnx_encoder)
    schema = (
        model.create_schema()
        .entities({
            "amount": "The transaction amount with its currency. Not the balance, fee, transaction cost or limit.",
            "merchant": "The business, service or person the money was paid to or received from.",
            "reference": "Transaction reference or ID code.",
        })
        .classification("kind", list(GLINER_KINDS))
        .classification("category", list(GLINER_CATEGORIES))
    )

    def predict(text):
        r = model.extract(text, schema)
        ents = r.get("entities", {})
        span = (ents.get("amount") or [""])[0]
        raw = {
            "kind": GLINER_KINDS.get(r.get("kind")),
            "amount_text": span,
            "currency": currency_of(span, text),
            "merchant": (ents.get("merchant") or [None])[0],
            "refs": ents.get("reference") or [],
            "category": GLINER_CATEGORIES.get(r.get("category")),
        }
        return raw, json.dumps(r, ensure_ascii=False)

    return predict, lambda: None


def qwen_backend(path):
    proc = start_server(path)
    ask("warm up")
    return ask, proc.kill


def main(labels_path, model, onnx_encoder=None):
    rows = [json.loads(line) for line in open(labels_path) if line.strip()]
    predict, close = gliner_backend(model, onnx_encoder) if Path(model).is_dir() else qwen_backend(model)
    results, latencies = {}, []
    try:
        predict("warm up")
        for row in rows:
            t = time.perf_counter()
            raw, out = predict(row["text"])
            latencies.append(time.perf_counter() - t)
            results[row["id"]] = (guard(raw, row["text"]), raw, out)
    finally:
        close()

    fields = ("kind", "amount", "currency", "merchant", "category", "subscription", "refs")
    score = {k: [0, 0] for k in fields}
    real = {k: [0, 0] for k in fields}  # the user's own SMS only: the honest number once training data mimics formats
    false_pos, misses = [], []
    for row in rows:
        pred, raw, out = results[row["id"]]
        money = row["kind"] != ["other"]
        checks = {"kind": pred["kind"] in row["kind"]}
        if money:
            checks["amount"] = pred.get("amount") == row["amount"]
            checks["currency"] = pred.get("currency") == row["currency"]
            checks["subscription"] = pred.get("subscription") == row["subscription"]
            if row["merchant"]:
                p, e = squash(pred.get("merchant")), squash(row["merchant"])
                checks["merchant"] = bool(p) and (e in p or p in e)
            if row["category"]:
                checks["category"] = pred.get("category") in row["category"]
            if row["refs"]:
                checks["refs"] = any(r in pred.get("refs", []) for r in row["refs"])
        elif pred["kind"] != "other":
            false_pos.append(row["id"])
        for k, ok in checks.items():
            for tally in (score, real) if not row["synthetic"] else (score,):
                tally[k][0] += ok
                tally[k][1] += 1
        if not all(checks.values()):
            bad = ", ".join(k for k, ok in checks.items() if not ok)
            misses.append(f"{row['id']} [{bad}] raw={json.dumps(raw, ensure_ascii=False)}"
                          + (f" ({pred['dropped']})" if pred.get("dropped") else ""))

    dup_pairs = [(r["dup_of"], r["id"]) for r in rows if r.get("dup_of")]
    dedup = sum(bool(set(results[a][0].get("refs", [])) & set(results[b][0].get("refs", []))) for a, b in dup_pairs)

    print(f"model: {model.rsplit('/', 1)[-1]}   messages: {len(rows)}")
    print(f"  {'':<13}{'all':>10}   {'real SMS only':>16}")
    for k, (ok, n) in score.items():
        rok, rn = real[k]
        real_cell = f"{rok:>3}/{rn:<3} {100 * rok / rn:5.1f}%" if rn else "     -"
        print(f"  {k:<13}{ok:>3}/{n:<3} {100 * ok / n:5.1f}%   {real_cell}")
    print(f"  false positives on non-money messages: {len(false_pos)} {false_pos}")
    print(f"  duplicates caught by shared ref: {dedup}/{len(dup_pairs)}")
    print(f"  latency (M1 CPU, 4 threads): mean {sum(latencies) / len(latencies):.2f}s, max {max(latencies):.2f}s")
    print("\nmisses:")
    print("\n".join(misses) or "  none")


if __name__ == "__main__":
    main(*sys.argv[1:4])
