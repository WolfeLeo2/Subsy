"""Generates synthetic training messages for GLiNER from templates of real formats.

Usage: python3 -I eval/make_train.py <labels.jsonl> <out.jsonl> [count]
Writes InputExample-shaped JSONL (text, entities, kind, category). Refuses to write if any generated text equals an eval
message, so the eval stays unseen. Labels must match GLINER_KINDS / GLINER_CATEGORIES in run_eval.py.
"""

import json
import random
import string
import sys

R = random.Random(7)

KIND = {
    "expense": "payment or money sent",
    "income": "money received or refund",
    "transfer": "transfer between own accounts",
    "other": "promotion, code, loan, failed transaction or chat",
}
CAT = {
    "GROCERIES": "supermarket or groceries", "DINING": "restaurant, cafe or coffee", "TRANSPORT": "taxi, fuel or fares",
    "HOUSING": "rent, electricity, water or home internet", "MOBILE": "airtime, data bundles or phone plan",
    "SHOPPING": "shopping", "HEALTH": "health or pharmacy", "ENTERTAINMENT": "streaming, TV or games",
    "TRAVEL": "travel", "EDUCATION": "education", "GIFTS": "gift", "SALARY": "salary", "REFUNDS": "refund or reversal",
    "OTHER": "other",
}

FIRST = ["JOHN", "MARY", "PETER", "GRACE", "BRIAN", "FAITH", "KEVIN", "MERCY", "DENNIS", "JOY", "SAMUEL", "WANJIRU",
         "OTIENO", "AKINYI", "MUTUA", "CHEBET", "KIPROTICH", "NJERI", "OMONDI", "ACHIENG", "Esther", "Collins", "Ian"]
LAST = ["KAMAU", "OCHIENG", "WAMBUI", "MWANGI", "KIPLAGAT", "NYAMBURA", "ODHIAMBO", "MUTISYA", "KARIUKI", "WEKESA",
        "Njoroge", "Chepkoech", "Owino", "Maina"]
# (merchant as written, the span to label, category)
TILLS = [("Naivas Supermarket Ruaka", "Naivas Supermarket", "GROCERIES"), ("QUICKMART LIMITED THIKA ROAD", "QUICKMART", "GROCERIES"),
         ("Carrefour Two Rivers", "Carrefour", "GROCERIES"), ("JAVA HOUSE ABC PLACE", "JAVA HOUSE", "DINING"),
         ("KFC Westlands", "KFC", "DINING"), ("Artcaffe Market", "Artcaffe", "DINING"), ("Mama Oliech Restaurant", "Mama Oliech Restaurant", "DINING"),
         ("RUBIS ENERGY KILIMANI", "RUBIS ENERGY", "TRANSPORT"), ("TotalEnergies Langata", "TotalEnergies", "TRANSPORT"),
         ("GOODLIFE PHARMACY", "GOODLIFE PHARMACY", "HEALTH"), ("Haltons Pharmacy", "Haltons Pharmacy", "HEALTH"),
         ("Bata Shoe Store Moi Avenue", "Bata", "SHOPPING"), ("PHONE PLACE KENYA", "PHONE PLACE KENYA", "SHOPPING"),
         ("Text Book Centre", "Text Book Centre", "EDUCATION")]
PAYBILLS = [("KPLC PREPAID", "KPLC PREPAID", "HOUSING"), ("NAIROBI WATER", "NAIROBI WATER", "HOUSING"),
            ("ZUKU FIBER", "ZUKU FIBER", "HOUSING"), ("SAFARICOM HOME FIBRE", "SAFARICOM HOME FIBRE", "HOUSING"),
            ("SAFARICOM DATA BUNDLES", "SAFARICOM DATA BUNDLES", "MOBILE"), ("SAFARICOM POSTPAID BUNDLES", "SAFARICOM POSTPAID BUNDLES", "MOBILE"),
            ("AIRTEL MONEY", "AIRTEL MONEY", "MOBILE"), ("DSTV KENYA", "DSTV KENYA", "ENTERTAINMENT"), ("GOtv Kenya", "GOtv", "ENTERTAINMENT"),
            ("SHOWMAX", "SHOWMAX", "ENTERTAINMENT"), ("KENYA AIRWAYS", "KENYA AIRWAYS", "TRAVEL"), ("UNIVERSITY OF NAIROBI FEES", "UNIVERSITY OF NAIROBI", "EDUCATION"),
            ("JUBILEE INSURANCE", "JUBILEE INSURANCE", "HEALTH"), ("LITTLE CAB", "LITTLE CAB", "TRANSPORT")]
CARD = [("PAYPAL *NETFLIX.COM", "NETFLIX.COM", "ENTERTAINMENT"), ("NETFLIX.COM", "NETFLIX.COM", "ENTERTAINMENT"),
        ("SPOTIFY", "SPOTIFY", "ENTERTAINMENT"), ("Spotify AB", "Spotify", "ENTERTAINMENT"), ("GOOGLE *YouTube", "YouTube", "ENTERTAINMENT"),
        ("GOOGLE *Google One", "Google One", "OTHER"), ("APPLE.COM/BILL", "APPLE.COM", "OTHER"), ("PAYPAL *UBER BV", "UBER BV", "TRANSPORT"),
        ("UBER *TRIP", "UBER", "TRANSPORT"), ("BOLT.EU/O/2410", "BOLT.EU", "TRANSPORT"), ("OPENAI *CHATGPT SUBSCR", "CHATGPT", "OTHER"),
        ("CANVA* 04512", "CANVA", "OTHER"), ("AMAZON PRIME*2K4", "AMAZON PRIME", "ENTERTAINMENT"), ("STEAMGAMES.COM", "STEAMGAMES.COM", "ENTERTAINMENT"),
        ("ALIEXPRESS", "ALIEXPRESS", "SHOPPING"), ("BOOKING.COM", "BOOKING.COM", "TRAVEL")]
CARD_TAIL = ["NL", "SE", "US", "IE", "GB", "SG", "LU"]
WORLD = [("Starbucks", "DINING"), ("Whole Foods Market", "GROCERIES"), ("Tesco", "GROCERIES"), ("Shell", "TRANSPORT"),
         ("Lyft", "TRANSPORT"), ("Netflix", "ENTERTAINMENT"), ("Spotify", "ENTERTAINMENT"), ("Disney Plus", "ENTERTAINMENT"),
         ("CVS Pharmacy", "HEALTH"), ("Zara", "SHOPPING"), ("Amazon", "SHOPPING"), ("Vodafone", "MOBILE"), ("Airbnb", "TRAVEL"),
         ("Pret A Manger", "DINING"), ("Lidl", "GROCERIES"), ("EDEKA", "GROCERIES"), ("Jumia", "SHOPPING"), ("Shoprite", "GROCERIES")]


def ref(n=10):
    return "U" + "".join(R.choice(string.ascii_uppercase + string.digits) for _ in range(n - 1))


def kref():
    return "D" + "".join(R.choice(string.ascii_uppercase + string.digits) for _ in range(9))


def person():
    return f"{R.choice(FIRST)} {R.choice(LAST)}"


def phone():
    return "07" + "".join(R.choice(string.digits) for _ in range(8))


def money(lo, hi, cents=True):
    v = R.uniform(lo, hi) if cents and R.random() < 0.3 else R.randint(int(lo), int(hi))
    return f"{v:,.2f}"


def date():
    return f"{R.randint(1, 28)}/{R.randint(1, 12)}/26 at {R.randint(1, 12)}:{R.randint(0, 59):02d} {R.choice(['AM', 'PM'])}"


def ex(text, kind, category="OTHER", amount=None, merchant=None, refs=()):
    ents = {}
    for label, values in (("amount", [amount] if amount else []), ("merchant", [merchant] if merchant else []),
                          ("reference", list(refs))):
        values = [v for v in values if v]
        assert all(v in text for v in values), (label, values, text)
        if values:
            ents[label] = values
    return {"text": text, "entities": ents, "kind": KIND[kind], "category": CAT[category]}


def bal():
    return money(100, 60000)


def mpesa_tail():
    return R.choice(["", " Amount you can transact within the day is " + money(400000, 499999) + ".",
                     " See all your balances now https://saf.cx/" + "".join(R.choice(string.ascii_letters) for _ in range(5))])


def gen():
    c = R.randint
    a = money(10, 9000)
    # Extra weight on what round one got wrong: airtime (6, 17), Airtel bundles (18), foreign card charges (19-21, 24).
    t = R.choice(list(range(28)) + [6, 6, 17, 17, 18, 19, 19, 20, 20, 21, 24])
    if t == 0:
        name, span, cat = R.choice(PAYBILLS)
        r = ref()
        return ex(f"{r} Confirmed. Ksh{a} sent to {name} for account {R.choice([name, str(c(10000, 9999999)), 'SHook Data Daily'])} on {date()}. "
                  f"New M-PESA balance is Ksh{bal()}. Transaction cost, Ksh0.00.{mpesa_tail()}", "expense", cat, f"Ksh{a}", span, [r])
    if t == 1:
        name, span, cat = R.choice(TILLS)
        r = ref()
        return ex(f"{r} Confirmed. Ksh{a} paid to {name}. on {date()}.New M-PESA balance is Ksh{bal()}. Transaction cost, Ksh0.00."
                  f"{mpesa_tail()}", "expense", cat, f"Ksh{a}", span, [r])
    if t == 2:
        p, r, fee = person(), ref(), R.choice(["0.00", "7.00", "13.00", "23.00", "33.00"])
        return ex(f"{r} Confirmed. Ksh{a} sent to {p} {phone()} on {date()}. New M-PESA balance is Ksh{bal()}. Transaction cost, Ksh{fee}."
                  f"{mpesa_tail()}", "expense", R.choice(["OTHER", "GIFTS"]), f"Ksh{a}", p, [r])
    if t == 3:
        p, r = person(), ref()
        return ex(f"{r} Confirmed.You have received Ksh{a} from {p} {phone()[:4]}***{c(100, 999)} on {date()}  New M-PESA balance is "
                  f"Ksh{bal()}. {R.choice(['', 'Invest & earn daily interest with ZIIDI on https://saf.cx/abc12', 'Separate personal and business funds through Pochi la Biashara on *334#.'])}",
                  "income", "OTHER", f"Ksh{a}", p, [r])
    if t in (4, 5):
        name, span, cat = R.choice(CARD)
        r, a = ref(), money(30, 3000)
        return ex(f"{r} Confirmed. Ksh{a} sent to M-PESA CARD for account {name}{' ' * c(1, 9)}{c(10**9, 10**11)}  {R.choice(CARD_TAIL)} on {date()} "
                  f"New M-PESA balance is Ksh{bal()}. Transaction cost, Ksh0.00.{mpesa_tail()}", "expense", cat, f"Ksh{a}", span, [r])
    if t == 6:
        r, a = ref(), money(5, 1000, cents=False)
        for_who = R.choice(["", f" for 2547{c(10000000, 99999999)}"])
        return ex(f"{r} Confirmed. You bought Ksh{a} of airtime{for_who} on {date()}. New M-PESA balance is Ksh{bal()}. Transaction cost, Ksh0.00.",
                  "expense", "MOBILE", f"Ksh{a}", None, [r])
    if t == 7:
        r, f = ref(), money(20, 3000)
        return ex(f"{r} Confirmed. Fuliza M-Pesa amount is Ksh {f}. Access Fee charged Ksh {money(1, 30)}. Total Fuliza M-Pesa outstanding amount "
                  f"is Ksh {money(20, 3000)} due on {c(1, 28)}/{c(1, 12)}/26. To check daily charges, Dial *234*0#OK Select Query Charges", "other")
    if t == 8:
        return ex(R.choice([
            f"{ref()} Failed. Insufficient funds in your M-PESA account as well as Fuliza M-PESA to send Ksh{a}. M-PESA balance is Ksh{money(1, 500)}.",
            f"Failed. You do not have enough money in your M-PESA account to pay Ksh{a} to {R.choice(TILLS)[0]}. Your balance is Ksh{money(1, 500)}.",
            f"Your request to send Ksh{a} to {person()} was not completed. Please try again later."]), "other")
    if t == 9:
        r, orig = ref(), ref()
        return ex(f"{r} Confirmed. Reversal of transaction {orig} has been successfully reversed on {date()} and Ksh{a} is credited to your M-PESA account. "
                  f"New M-PESA account balance is Ksh{bal()}.", "income", "REFUNDS", f"Ksh{a}", None, [r])
    if t == 10:
        return ex(R.choice([
            f"Get {c(1, 10)}GB for Ksh {c(20, 250)} valid {c(1, 24)}hrs! Dial *544# now. Offer ends today.",
            f"Pata extra cash with a flexible Mobile Loan of up to KES {c(10, 500)}K. Dial *522# or use the KCB App.",
            f"You qualify for an M-Shwari loan of up to Ksh {money(500, 50000)}. Dial *334# to borrow now.",
            f"Tunukiwa! Buy Ksh {c(10, 100)} airtime and get {c(50, 500)}MB free. Dial *444#.",
            f"Your Fuliza M-PESA limit is now Ksh {money(500, 20000)}. Use it to complete your payments.",
            f"Earn up to {c(8, 15)}% interest p.a. with ZIIDI. Invest from Ksh 100 today on *334#."]), "other")
    if t == 11:
        return ex(R.choice([
            f"Your {R.choice(['KCB', 'Equity', 'M-PESA', 'NCBA', 'Google'])} verification code is {c(100000, 999999)}. It expires in {c(2, 10)} minutes. Do not share it.",
            f"{c(1000, 9999)} is your one-time PIN. Do not share this code with anyone.",
            f"You have purchased {c(1, 20)}{R.choice(['GB', 'MB'])} data valid until {c(1, 28)}/{c(1, 12)}/2026 23:59. Dial *544# to check your balance.",
            f"Your data bundle balance is {c(10, 999)}MB valid until {c(1, 28)}/{c(1, 12)}/2026."]), "other")
    if t == 12:
        k, a = kref(), money(100, 20000)
        return ex(f"{k} completed.KES {a} sent to M-PESA 2547{c(10000000, 99999999)} on {c(1, 28):02d}/{c(1, 12):02d}/2026 at {c(1, 12)}:{c(0, 59):02d} PM."
                  f"Transaction cost KES {R.choice(['11.90', '22.80', '33.00'])} Incl. Tax Amount KES 0.90.{R.choice(['', '  Pata extra cash with a flexible Mobile Loan. Dial *522# or use the KCB App to check limit.'])}",
                  "expense", "OTHER", f"KES {a}", None, [k])
    if t == 13:
        owner, k, r, a = person(), kref(), ref(), money(100, 20000)
        # The tokenizer keeps "2547****304-NAME" as one run, so the whole run is the merchant; the app strips the number.
        p = f"2547****{c(100, 999)}-{person()}"
        return ex(f"Dear {owner} you have successfully transferred KES {a} to  {p} on {c(1, 28):02d}/{c(1, 12):02d}/2026 at "
                  f"{c(1, 12)}:{c(0, 59):02d} PM. M-PESA Ref {r},Transaction Ref No {k}.", "expense", "OTHER", f"KES {a}", p, [r, k])
    if t == 14:
        p, k, r, a = person(), kref(), ref(), R.choice([f"{c(100, 50000)}.0", money(100, 50000)])
        return ex(f"You have received KES {a} from {p}. M-PESA Ref {r}. Transaction Ref No {k}", "income", "OTHER", f"KES {a}", p, [r, k])
    if t == 15:
        p, k, a = person(), "MB" + "".join(R.choice(string.ascii_uppercase + string.digits) for _ in range(14)), money(100, 50000)
        return ex(f"{k} Confirmed.  KES {a} received from {p} - {c(100, 999)}****{c(100, 999)} on 2026-{c(1, 12):02d}-{c(1, 28):02d} {c(1, 12):02d}:{c(0, 59):02d}:{c(0, 59):02d} PM via KCB.",
                  "income", "OTHER", f"KES {a}", p, [k])
    if t == 16:
        owner, emp, a = person(), R.choice(["SAFARICOM PLC", "ACME LTD", "KENGEN", "EQUITY BANK", "BRIGHT SOLUTIONS LTD"]), money(15000, 300000, cents=False)
        return ex(f"Dear {owner}, your account {c(10, 99)}****{c(100, 999)} has been credited with KES {a} from {emp} SALARY on {c(1, 28):02d}/{c(1, 12):02d}/2026. "
                  f"Avail bal KES {bal()}.", "income", "SALARY", f"KES {a}", emp, [])
    if t == 17:
        r, a = ref(), money(10, 1000, cents=False)
        return ex(f"{r} Confirmed. You have bought airtime worth KES {a} for 07{c(10000000, 99999999)} via {R.choice(['KCB', 'Equity'])} on "
                  f"{c(1, 28)}/{c(1, 12)}/2026 at {c(1, 12)}:{c(0, 59):02d} AM.", "expense", "MOBILE", f"KES {a}", None, [r])
    if t == 18:
        a, tid = money(20, 1500), f"MP{c(100000, 999999)}.{c(1000, 9999)}.A{c(10000, 99999)}"
        return ex(f"Dear Customer, you have bought {c(1, 20)}GB data bundle at KES {a} valid for {c(1, 30)} days. Airtel Money balance KES {bal()}. Trans ID: {tid}.",
                  "expense", "MOBILE", f"KES {a}", "Airtel", [tid])
    if t == 19:
        m, cat = R.choice(WORLD)
        a = f"{R.uniform(1, 300):.2f}"
        return ex(f"{R.choice(['Chase', 'Capital One', 'Citi', 'Wells Fargo'])}: A charge of ${a} at {m} was authorized on {c(1, 12)}/{c(1, 28)} on your card ending in {c(1000, 9999)}.",
                  "expense", cat, f"${a}", m)
    if t == 20:
        m, cat = R.choice(WORLD)
        cur = R.choice(["£", "€", "$"])
        a = f"{cur}{R.uniform(1, 300):.2f}"
        return ex(R.choice([f"You paid {a} to {m}", f"Paid {a} at {m}", f"{a} spent at {m}"]), "expense", cat, a, m)
    if t == 21:
        m, cat = R.choice(WORLD)
        a = f"{R.uniform(1, 300):.2f}"
        return ex(f"Google Wallet: ${a} at {m} with {R.choice(['Visa', 'Mastercard'])} ••{c(1000, 9999)}", "expense", cat, f"${a}", m)
    if t == 22:
        m, cat = R.choice(WORLD)
        a = f"NGN{c(500, 200000):,}.00"
        return ex(f"Acct: 0{c(10, 99)}****{c(100, 999)} Amt: {a} DR Desc: POS PURCHASE {m.upper()} Date: {c(1, 28)}-Oct-2026 Bal: NGN{c(1000, 900000):,}.{c(10, 99)}",
                  "expense", cat, a, m.upper())
    if t == 23:
        m, cat = R.choice(WORLD)
        a, u = f"Rs.{c(10, 9000)}.00", str(c(10**11, 10**12 - 1))
        return ex(f"{a} debited from A/c XX{c(1000, 9999)} on {c(1, 28)}-10-26 to VPA {m.lower().replace(' ', '')}@ybl (UPI Ref {u}). Not you? Call 1800{c(1000, 9999)}",
                  "expense", cat, a, m.lower().replace(" ", "") + "@ybl", [u])
    if t == 24:
        m, cat = R.choice(WORLD)
        a = f"{c(1, 300)},{c(10, 99)} EUR"
        return ex(R.choice([f"Kartenzahlung {a} bei {m} Berlin. Kontostand: {c(1, 9)}.{c(100, 999)},{c(10, 99)} EUR",
                            f"Paiement par carte de {a} chez {m} Paris. Solde : {c(100, 999)},{c(10, 99)} EUR"]), "expense", cat, a, m)
    if t == 25:
        p = person().title()
        a = f"{c(1, 500)},{c(10, 99)} €"
        return ex(R.choice([f"Has recibido {a} de {p} por Bizum", f"Vous avez reçu {a} de {p}"]), "income", "OTHER", a, p)
    if t == 26:
        m, _ = R.choice(WORLD)
        a = f"${R.uniform(1, 300):.2f}"
        return ex(R.choice([f"Your refund of {a} from {m} has been issued to your Visa ending in {c(1000, 9999)}.",
                            f"Refund: {a} from {m} was credited to your card."]), "income", "REFUNDS", a, m)
    return ex(R.choice([
        f"bro send me {c(100, 5000)} by tonight, nitakurudishia {R.choice(['kesho', 'friday', 'next week'])}",
        f"Si unitumie ksh {c(50, 1000)} ya {R.choice(['fare', 'lunch', 'airtime'])} tafadhali",
        f"I owe you like {c(100, 5000):,} from last week, will sort you out",
        f"Meeting at {c(1, 12)}pm, bring the {c(2, 20)} copies",
        f"Rent is {c(5, 50)}k this month, can we split?",
        f"Hey, did you get the KES {c(100, 3000)} I sent yesterday?"]), "other")


def main(labels_path, out_path, count=600):
    held_out = {json.loads(line)["text"] for line in open(labels_path) if line.strip()}
    rows, seen = [], set()
    while len(rows) < count:
        row = gen()
        if row["text"] in seen:
            continue
        assert row["text"] not in held_out, row["text"]
        seen.add(row["text"])
        rows.append(row)
    with open(out_path, "w") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    kinds = {}
    for row in rows:
        kinds[row["kind"]] = kinds.get(row["kind"], 0) + 1
    print(f"wrote {len(rows)} examples to {out_path}: {kinds}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 600)
