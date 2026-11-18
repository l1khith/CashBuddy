"""Generate the CashBuddy synthetic gate dataset.

Two clean classes:
  positives = real bank / UPI / card / ATM transaction messages
  negatives = false flags, promos, OTPs, balance alerts, chat noise, failed txns

Run:
    python trainingdata/generate_dataset.py --positives 20000 --negatives 20000 --seed 42
"""

from __future__ import annotations

import argparse
import calendar
import hashlib
import json
import math
import random
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from formatters import (  # noqa: E402
    AMOUNT_RANGES, BASE_DATE, amount_to_text, date_text, group_indian,
    long_datetime_text, make_acct, make_amount, make_datetime, make_otp,
    make_safe_phone, make_utr, make_vpa, month_text, time_text,
)
from templates_neg import NEGATIVE_TEMPLATES, TEMPLATE_VERSION as NEG_VERSION  # noqa: E402
from templates_pos import POSITIVE_TEMPLATES, TEMPLATE_VERSION as POS_VERSION  # noqa: E402
from vocab import (  # noqa: E402
    ALL_CATEGORIES, BANKS, CHAT_PEOPLE, CHAT_PREVIEWS_FINANCIAL,
    CHAT_PREVIEWS_PLAIN, CHAT_SENDERS, CITIES, EXPENSE_CATEGORIES,
    GENERIC_APPS, HEADLINES, INCOME_CATEGORIES, INSTAGRAM_USERS, MERCHANT_TABLE,
    PEOPLE, SOCIAL_APPS, SONGS, ARTISTS, SUBJECTS, UPI_APPS, WALLET_APPS,
    build_merchants,
)

DATASET_VERSION = "1.0.0"
SCHEMA_VERSION = "1.0.0"
SPLITS = ("train", "val", "test")
SPLIT_RATIO = {"train": 0.70, "val": 0.15, "test": 0.15}
SPLIT_PREFIX = {"train": "tr", "val": "va", "test": "te"}

NEG_WEIGHTS = {
    "PROMO": 3000,
    "BANK_PROMO": 1200,
    "OTP": 2000,
    "BALANCE_ONLY": 1400,
    "STATEMENT": 600,
    "CREDIT_SCORE": 400,
    "CHAT_SUMMARY": 2800,
    "SOCIAL": 2300,
    "NEWS_JOB": 1200,
    "GENERIC": 1400,
    "FAILED_TXN": 1400,
    "PENDING_TXN": 700,
    "REVERSED": 400,
    "COLLECT_REQUEST": 500,
    "MANDATE": 400,
    "REWARD": 300,
}

BUREAUS = ["CIBIL", "Experian", "Equifax", "CRIF"]
ROLES = [
    "Software Engineer", "Data Analyst", "Product Manager", "QA Engineer",
    "DevOps Engineer", "Business Analyst", "UI Designer", "Intern",
    "Customer Support", "Sales Executive", "Content Writer", "HR Associate",
]
COMPANIES = [
    "Infosys", "TCS", "Wipro", "Accenture", "Cognizant", "HCLTech",
    "IBM India", "Capgemini", "Deloitte", "Amazon India", "Flipkart",
    "Swiggy", "Zomato", "PhonePe", "Razorpay",
]
COMMENTS = [
    "Nice shot!", "Where is this?", "Totally agree", "Let's plan for Sunday",
    "Congratulations!", "This is great", "Send the link please", "Haha true",
    "Miss you all", "Count me in", "Well deserved", "See you there",
]
TWEETS = [
    "Just shipped something new", "What a match!", "Weekend plans?",
    "This city never sleeps", "Coffee and code", "Back to work tomorrow",
]
VIDEOS = [
    "Episode {n}: Getting started", "Episode {n}: Deep dive",
    "Episode {n}: Q&A", "Episode {n}: Behind the scenes",
    "Episode {n}: Top 10 tips",
]


def allocate(total: int, weights: dict[str, float]) -> dict[str, int]:
    """Largest-remainder allocation that sums exactly to total."""
    if total <= 0:
        return {k: 0 for k in weights}
    wsum = sum(weights.values())
    exact = {k: total * v / wsum for k, v in weights.items()}
    base = {k: int(math.floor(v)) for k, v in exact.items()}
    remainder = total - sum(base.values())
    order = sorted(exact, key=lambda k: (-(exact[k] - base[k]), k))
    for k in order[:remainder]:
        base[k] += 1
    return base


def split_templates(templates: list[dict], group_key: str,
                    rng: random.Random) -> dict[str, list[dict]]:
    groups: dict[str, list[dict]] = defaultdict(list)
    for t in templates:
        groups[t[group_key]].append(t)
    out: dict[str, list[dict]] = {s: [] for s in SPLITS}
    for group in sorted(groups):
        items = groups[group][:]
        rng.shuffle(items)
        n = len(items)
        if n < 3:
            raise ValueError(f"template group {group!r} needs >=3 variants, got {n}")
        n_val = max(1, int(n * 0.15 + 0.5))
        n_test = max(1, int(n * 0.15 + 0.5))
        while n_val + n_test >= n:
            if n_val >= n_test and n_val > 1:
                n_val -= 1
            elif n_test > 1:
                n_test -= 1
            else:
                raise ValueError(f"cannot split group {group!r} with n={n}")
        n_train = n - n_val - n_test
        out["train"].extend(items[:n_train])
        out["val"].extend(items[n_train:n_train + n_val])
        out["test"].extend(items[n_train + n_val:])
    return out


def split_merchants(merchants, rng: random.Random):
    by_cat: dict[str, list] = defaultdict(list)
    for m in merchants:
        by_cat[m.category].append(m)
    out = {s: defaultdict(list) for s in SPLITS}
    for cat in ALL_CATEGORIES:
        items = by_cat[cat][:]
        rng.shuffle(items)
        n = len(items)
        n_test = max(2, n // 4)
        n_val = max(2, n // 6)
        n_train = n - n_val - n_test
        if n_train < 1:
            raise ValueError(f"not enough merchants for {cat}")
        for m in items[:n_train]:
            out["train"][cat].append(m)
        for m in items[n_train:n_train + n_val]:
            out["val"][cat].append(m)
        for m in items[n_train + n_val:]:
            out["test"][cat].append(m)
    return out


def split_people(rng: random.Random):
    items = PEOPLE[:]
    rng.shuffle(items)
    n = len(items)
    n_test = max(2, int(n * 0.2 + 0.5))
    n_val = max(2, int(n * 0.15 + 0.5))
    n_train = n - n_val - n_test
    return {
        "train": items[:n_train],
        "val": items[n_train:n_train + n_val],
        "test": items[n_train + n_val:],
    }


def ms(dt: datetime) -> int:
    return calendar.timegm(dt.utctimetuple()) * 1000


class Builder:
    def __init__(self, split: str, rng: random.Random, merchants_by_cat,
                 people: list[str], pos_templates: list[dict],
                 neg_templates: list[dict], used_texts: set[str]):
        self.split = split
        self.rng = rng
        self.merchants_by_cat = merchants_by_cat
        self.people = people
        self.pos_templates = pos_templates
        self.neg_templates = neg_templates
        self.used_texts = used_texts
        self.rows: list[dict] = []
        self.id_counter = 0
        self.group_counter = 0

    def next_id(self) -> str:
        self.id_counter += 1
        return f"{SPLIT_PREFIX[self.split]}-{self.id_counter:06d}"

    def next_group(self) -> str:
        self.group_counter += 1
        return f"grp-{SPLIT_PREFIX[self.split]}-{self.group_counter:06d}"

    def _pick_merchant(self, template: dict, category: str):
        rng = self.rng
        kind = template["merchant_kind"]
        if kind == "none":
            return None
        if kind == "person":
            name = rng.choice(self.people)
            slug = "".join(ch for ch in name.lower() if ch.isalnum())
            return {"raw": name, "canonical": name, "id": f"p_{slug}",
                    "type": "PERSON"}
        if kind == "atm":
            atm = next((m for m in self.merchants_by_cat["Uncategorized"]
                        if m.id == "m_atm"), None)
            name = atm.name if atm else "ATM Withdrawal"
            return {"raw": f"ATM {rng.choice(CITIES)}", "canonical": name,
                    "id": atm.id if atm else "m_atm", "type": "BANK"}
        pool = self.merchants_by_cat.get(category) or self.merchants_by_cat["Uncategorized"]
        m = rng.choice(pool)
        return {"raw": m.name, "canonical": m.name, "id": m.id, "type": m.type}

    def _format_merchant(self, merchant, style: str) -> str:
        if merchant is None:
            return ""
        name = merchant["canonical"]
        if merchant["type"] == "PERSON":
            return name
        if style == "upper":
            raw = name.upper()
        elif style == "title":
            raw = name
        else:
            raw = name
        if style == "ocr" or (style == "upper" and self.rng.random() < 0.04):
            chars = list(raw)
            for i, ch in enumerate(chars):
                if ch == "I" and self.rng.random() < 0.5:
                    chars[i] = "1"
                elif ch == "O" and self.rng.random() < 0.5:
                    chars[i] = "0"
                elif ch == "L" and self.rng.random() < 0.15:
                    chars[i] = "1"
            raw = "".join(chars)
        return raw

    def _source(self, source: str, bank, app) -> dict:
        rng = self.rng
        if source == "bank_sms":
            return dict(source_type="BANK_SMS", package_name=bank.package,
                        sender_id=bank.sender, title=None, bank=bank.name)
        if source == "bank_app":
            return dict(source_type="BANK_APP", package_name=bank.package,
                        sender_id=None, bank=bank.name,
                        title=rng.choice(["Transaction Alert", "Payment Alert",
                                          "Account Alert", "Card Alert"]))
        if source == "bank_promo":
            return dict(source_type="BANK_SMS", package_name=bank.package,
                        sender_id=bank.sender, title=None, bank=bank.name)
        if source == "card":
            return dict(source_type="CARD_ALERT", package_name=bank.package,
                        sender_id=bank.sender, title="Card Alert", bank=bank.name)
        if source == "atm":
            return dict(source_type="ATM", package_name=bank.package,
                        sender_id=bank.sender, title=None, bank=bank.name)
        if source == "upi":
            return dict(source_type="UPI_APP", package_name=app.package,
                        sender_id=None, title=app.name, bank=bank.name)
        if source == "wallet":
            return dict(source_type="WALLET", package_name=app.package,
                        sender_id=None, title=app.name, bank=bank.name)
        if source == "screenshot":
            return dict(source_type="SCREENSHOT", package_name=app.package,
                        sender_id=None, title=None, bank=bank.name)
        if source == "messaging":
            return dict(source_type="MESSAGING_FORWARD",
                        package_name="com.whatsapp", sender_id=None,
                        title="WhatsApp", bank=bank.name)
        if source == "chat":
            return dict(source_type="MESSAGING", package_name="com.whatsapp",
                        sender_id=None, title="WhatsApp", bank=None)
        if source == "social":
            return dict(source_type="APP_NOTIFICATION", package_name=app.package,
                        sender_id=None, title=app.name, bank=None)
        if source == "linkedin":
            return dict(source_type="APP_NOTIFICATION",
                        package_name="com.linkedin.android", sender_id=None,
                        title="LinkedIn", bank=None)
        if source == "email":
            return dict(source_type="EMAIL", package_name="com.google.android.gm",
                        sender_id=None, title="Gmail", bank=None)
        if source == "generic":
            return dict(source_type="APP_NOTIFICATION", package_name=app.package,
                        sender_id=None, title=app.name, bank=None)
        raise ValueError(f"unknown source {source!r}")

    def _chat_or_social_app(self, source: str):
        rng = self.rng
        if source == "chat":
            return None
        if source == "social":
            return rng.choice(SOCIAL_APPS)
        if source == "generic":
            return rng.choice(GENERIC_APPS)
        return None

    def _base_slots(self, template: dict, bank, app, dt, amount: float,
                    amt_text: str, merchant, utr: str) -> dict:
        rng = self.rng
        acct = make_acct(rng)
        bal_num, bal_text = amount_to_text(
            rng, amount + rng.uniform(100, 200000))
        limit_num, limit_text = amount_to_text(
            rng, amount + rng.uniform(5000, 500000))
        min_num, min_text = amount_to_text(rng, max(10, amount * 0.05))
        vpa = make_vpa(rng, merchant["canonical"] if merchant else "merchant")
        person = (merchant["canonical"] if merchant and merchant["type"] == "PERSON"
                  else rng.choice(self.people))
        city = rng.choice(CITIES)
        fmt = template["fmt"]
        slots = dict(
            bank=bank.name, bank_short=bank.short, bank_sender=bank.sender,
            acct=acct, amt=amt_text, bal=bal_text, limit=limit_text,
            min=min_text, utr=utr, ref=utr, otp=make_otp(rng),
            phone6=make_safe_phone(rng), date=date_text(rng, dt),
            date2=long_datetime_text(rng, dt), time=time_text(rng, dt),
            month=month_text(rng, dt), city=city, atm=f"ATM {city}",
            app=app.name if app else "",
            merchant=self._format_merchant(merchant, template.get("style", "upper")),
            vpa=vpa, person=person, bureau=rng.choice(BUREAUS),
            score=rng.randint(300, 900), pct=rng.randint(5, 50),
            mins=rng.randint(5, 30), days=rng.randint(3, 7),
            count=rng.randint(2, 12), chats=rng.randint(2, 8),
            sender_name=rng.choice(CHAT_PEOPLE + CHAT_SENDERS),
            sender=rng.choice(CHAT_PEOPLE + CHAT_SENDERS),
            sender2=rng.choice(CHAT_PEOPLE),
            preview=rng.choice(CHAT_PREVIEWS_FINANCIAL if rng.random() < 0.5
                              else CHAT_PREVIEWS_PLAIN),
            preview2=rng.choice(CHAT_PREVIEWS_PLAIN),
            username=f"{rng.choice(INSTAGRAM_USERS)}{rng.randint(1, 999)}",
            comment=rng.choice(COMMENTS), subreddit=f"india{rng.randint(1, 99)}",
            channel=f"{rng.choice(INSTAGRAM_USERS)}{rng.randint(1, 99)}",
            video=f"Episode {rng.randint(1, 40)}: {rng.choice(['Getting started', 'Deep dive', 'Q&A', 'Top 10 tips'])}",
            tweet=rng.choice(TWEETS), subject=f"{rng.choice(SUBJECTS)} (#{rng.randint(100, 9999)})",
            role=rng.choice(ROLES), company=rng.choice(COMPANIES),
            headline=f"{rng.choice(HEADLINES)} - {city.title()}",
            song=f"{rng.choice(SONGS)}{rng.choice(['', ' (Live)', ' (Acoustic)', ' - Remix'])}",
            artist=rng.choice(ARTISTS),
            version=f"{rng.randint(1, 9)}.{rng.randint(0, 20)}.{rng.randint(0, 9)}",
        )
        if template["tid"].startswith("gen_"):
            slots["count"] = rng.randint(1, 99)
        if "{merchant" not in fmt and "{vpa}" not in fmt and "{person}" not in fmt:
            slots["merchant"] = ""
        return dict(slots=slots, acct=acct, balance=bal_num,
                    limit=limit_num, minimum=min_num, city=city)

    def _write_row(self, text: str, *, label: str, template: dict,
                   category: str | None, source_fields: dict, merchant,
                   amount, amt_text, slots: dict, acct: str,
                   balance, limit, negative_type: str | None,
                   timestamp: int, is_transaction: bool,
                   distractor=None) -> dict:
        row = {
            "id": self.next_id(),
            "schema_version": SCHEMA_VERSION,
            "dataset_version": DATASET_VERSION,
            "split": self.split,
            "label": label,
            "is_transaction": is_transaction,
            "is_financial_event": bool(template.get("is_financial_event", is_transaction)),
            "negative_type": negative_type,
            "source_type": source_fields["source_type"],
            "bank": source_fields["bank"],
            "package_name": source_fields["package_name"],
            "sender_id": source_fields["sender_id"],
            "title": source_fields["title"],
            "text": text,
            "amount": amount,
            "amount_text": amt_text,
            "distractor_amount": distractor,
            "currency": "INR",
            "txn_type": template.get("txn"),
            "status": "SUCCESS" if is_transaction else template.get("status"),
            "merchant_raw": merchant["raw"] if merchant else None,
            "merchant_canonical": merchant["canonical"] if merchant else None,
            "merchant_id": merchant["id"] if merchant else None,
            "merchant_type": merchant["type"] if merchant else None,
            "category": category,
            "account_last4": acct if ("{acct}" in template["fmt"]) else None,
            "balance": balance if "{bal}" in template["fmt"] else None,
            "available_limit": limit if "{limit}" in template["fmt"] else None,
            "utr": slots["utr"] if ("{utr}" in template["fmt"] or "{ref}" in template["fmt"]) else None,
            "payment_method": template.get("method"),
            "is_promo": bool(template.get("is_promo", False)),
            "difficulty": template.get("difficulty", "medium"),
            "template_id": template["tid"],
            "event_group_id": None,
            "duplicate_of": None,
            "timestamp_ms": timestamp,
        }
        return row

    def _row_merchant(self, template: dict, merchant):
        fmt = template["fmt"]
        if any(x in fmt for x in ("{merchant", "{vpa}", "{person}")):
            return merchant
        return None

    def make_positive(self, template: dict, category: str):
        rng = self.rng
        for _ in range(60):
            bank = rng.choice(BANKS)
            app = rng.choice(UPI_APPS)
            dt = make_datetime(rng)
            amount, amt_text = make_amount(rng, category, template.get("bounds"))
            merchant = self._pick_merchant(template, category)
            utr = make_utr(rng)
            built = self._base_slots(template, bank, app, dt, amount, amt_text,
                                     merchant, utr)
            fmt = template["fmt"]
            if "{app}" in fmt and template["source"] == "wallet":
                app = rng.choice(WALLET_APPS)
                built = self._base_slots(template, bank, app, dt, amount,
                                         amt_text, merchant, utr)
            text = fmt.format(**built["slots"])
            if text in self.used_texts:
                continue
            self.used_texts.add(text)
            source = self._source(template["source"], bank, app)
            row = self._write_row(
                text, label="TRANSACTION", template=template,
                category=category, source_fields=source,
                merchant=self._row_merchant(template, merchant),
                amount=amount, amt_text=amt_text, slots=built["slots"],
                acct=built["acct"], balance=built["balance"],
                limit=built["limit"], negative_type=None,
                timestamp=ms(dt), is_transaction=True,
            )
            ctx = dict(template=template, category=category, bank=bank,
                       app=app, dt=dt, amount=amount, amt_text=amt_text,
                       merchant=merchant, utr=utr)
            return row, ctx
        return None, None

    def make_companion(self, ctx: dict):
        rng = self.rng
        main = ctx["template"]
        category = ctx["category"]
        main_has_merchant = self._row_merchant(main, ctx["merchant"]) is not None
        cands = [
            t for t in self.pos_templates
            if t["tid"] != main["tid"]
            and t["txn"] == main["txn"]
            and t["source"] != main["source"]
            and t["merchant_kind"] == main["merchant_kind"]
            and category in t["cats"]
            and (self._row_merchant(t, ctx["merchant"]) is not None)
            == main_has_merchant
        ]
        if not cands:
            return None
        t = rng.choice(cands)
        for _ in range(20):
            bank = ctx["bank"]
            app = ctx["app"]
            dt = ctx["dt"]
            amt_text = ctx["amt_text"]
            if t["source"] == "upi":
                app = rng.choice(UPI_APPS)
            if t["source"] == "wallet":
                app = rng.choice(WALLET_APPS)
            utr = ctx["utr"] if rng.random() < 0.6 else make_utr(rng)
            built = self._base_slots(t, bank, app, dt, ctx["amount"], amt_text,
                                     ctx["merchant"], utr)
            text = t["fmt"].format(**built["slots"])
            if text in self.used_texts:
                continue
            self.used_texts.add(text)
            source = self._source(t["source"], bank, app)
            row = self._write_row(
                text, label="TRANSACTION", template=t, category=category,
                source_fields=source,
                merchant=self._row_merchant(t, ctx["merchant"]),
                amount=ctx["amount"], amt_text=amt_text, slots=built["slots"],
                acct=built["acct"], balance=built["balance"],
                limit=built["limit"], negative_type=None,
                timestamp=ms(dt), is_transaction=True,
            )
            return row
        return None

    def make_negative(self, template: dict, negative_type: str):
        rng = self.rng
        bounds = {
            "PROMO": (49, 10000),
            "BANK_PROMO": (199, 500000),
            "OTP": (10, 100000),
            "BALANCE_ONLY": (100, 50000),
            "STATEMENT": (100, 100000),
            "FAILED_TXN": (10, 100000),
            "PENDING_TXN": (10, 100000),
            "REVERSED": (10, 100000),
            "COLLECT_REQUEST": (10, 50000),
            "MANDATE": (99, 50000),
            "REWARD": (10, 5000),
        }.get(negative_type, (10, 100000))
        for _ in range(80):
            bank = rng.choice(BANKS)
            source = template["source"]
            if source == "upi":
                app = rng.choice(UPI_APPS)
            elif source == "wallet":
                app = rng.choice(WALLET_APPS)
            elif source == "social":
                app = rng.choice(SOCIAL_APPS)
            elif source == "generic":
                app = rng.choice(GENERIC_APPS)
            else:
                app = rng.choice(UPI_APPS)
            dt = make_datetime(rng)
            amount, amt_text = make_amount(
                rng, None, template.get("bounds") or bounds)
            merchant = self._pick_merchant(
                template, rng.choice(EXPENSE_CATEGORIES))
            utr = make_utr(rng)
            built = self._base_slots(template, bank, app, dt, amount,
                                     amt_text, merchant, utr)
            text = template["fmt"].format(**built["slots"])
            if text in self.used_texts:
                continue
            self.used_texts.add(text)
            row = self._write_row(
                text, label="NOT_TRANSACTION", template=template,
                category=None, source_fields=self._source(source, bank, app),
                merchant=self._row_merchant(template, merchant),
                amount=None, amt_text=None, slots=built["slots"],
                acct=built["acct"], balance=built["balance"],
                limit=built["limit"], negative_type=negative_type,
                timestamp=ms(dt), is_transaction=False, distractor=amount,
            )
            return row
        return None


def summarise(rows: list[dict]) -> dict:
    counter = lambda key: dict(sorted(Counter(r[key] for r in rows).items(), key=lambda kv: (-kv[1], str(kv[0]))))
    return {
        "total": len(rows),
        "by_label": counter("label"),
        "by_split": counter("split"),
        "by_source_type": counter("source_type"),
        "by_category": counter("category"),
        "by_negative_type": counter("negative_type"),
        "by_difficulty": counter("difficulty"),
    }


def write_jsonl(path: Path, rows: list[dict]) -> dict:
    path.parent.mkdir(parents=True, exist_ok=True)
    h = hashlib.sha256()
    with path.open("w", encoding="utf-8", newline="\n") as fh:
        for row in rows:
            line = json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n"
            h.update(line.encode("utf-8"))
            fh.write(line)
    return {
        "rows": len(rows),
        "bytes": path.stat().st_size,
        "sha256": h.hexdigest(),
    }


def generate(args) -> dict:
    rng = random.Random(args.seed)
    merchants = build_merchants()
    merchant_split = split_merchants(merchants, rng)
    people_split = split_people(rng)
    pos_split = split_templates(POSITIVE_TEMPLATES, "family", rng)
    neg_flat = split_templates(NEGATIVE_TEMPLATES, "negative_type", rng)
    neg_split: dict[str, dict[str, list[dict]]] = {
        s: defaultdict(list) for s in SPLITS}
    for split in SPLITS:
        for template in neg_flat[split]:
            neg_split[split][template["negative_type"]].append(template)
    used_texts: set[str] = set()

    pos_split_counts = allocate(args.positives, SPLIT_RATIO)
    neg_split_counts = allocate(args.negatives, SPLIT_RATIO)
    by_split: dict[str, list[dict]] = {}

    for split in SPLITS:
        b = Builder(
            split=split,
            rng=rng,
            merchants_by_cat=merchant_split[split],
            people=people_split[split],
            pos_templates=pos_split[split],
            neg_templates=neg_split[split],
            used_texts=used_texts,
        )
        cat_counts = allocate(
            pos_split_counts[split], {c: 1 for c in ALL_CATEGORIES})
        for category in ALL_CATEGORIES:
            expected = "CREDIT" if category in INCOME_CATEGORIES else "DEBIT"
            candidates = [
                t for t in pos_split[split]
                if category in t["cats"] and t["txn"] == expected
            ]
            if not candidates:
                raise RuntimeError(
                    f"no positive template for split={split} category={category}")
            target = cat_counts[category]
            made = 0
            guard = 0
            limit = target * 60 + 500
            while made < target and guard < limit:
                guard += 1
                template = rng.choice(candidates)
                row, ctx = b.make_positive(template, category)
                if row is None:
                    continue
                b.rows.append(row)
                made += 1
                if made < target and rng.random() < 0.10:
                    comp = b.make_companion(ctx)
                    if comp is not None:
                        group = b.next_group()
                        row["event_group_id"] = group
                        comp["event_group_id"] = group
                        comp["duplicate_of"] = row["id"]
                        b.rows.append(comp)
                        made += 1
            if made < target:
                print(f"  ! {split}/{category}: {made}/{target} positives "
                      f"(text collisions)")

        type_counts = allocate(neg_split_counts[split], NEG_WEIGHTS)
        for negative_type, target in type_counts.items():
            candidates = neg_split[split].get(negative_type, [])
            if not candidates:
                raise RuntimeError(
                    f"no negative template for split={split} type={negative_type}")
            made = 0
            guard = 0
            limit = target * 80 + 500
            while made < target and guard < limit:
                guard += 1
                template = rng.choice(candidates)
                row = b.make_negative(template, negative_type)
                if row is None:
                    continue
                b.rows.append(row)
                made += 1
            if made < target:
                print(f"  ! {split}/{negative_type}: {made}/{target} negatives "
                      f"(text collisions)")

        rng.shuffle(b.rows)
        by_split[split] = b.rows

    rows = by_split["train"] + by_split["val"] + by_split["test"]
    positives = [r for r in rows if r["is_transaction"]]
    negatives = [r for r in rows if not r["is_transaction"]]

    out: Path = args.out
    out.mkdir(parents=True, exist_ok=True)
    files = {}
    files["positive_bank_transactions.jsonl"] = write_jsonl(
        out / "positive_bank_transactions.jsonl", positives)
    files["negative_false_flags.jsonl"] = write_jsonl(
        out / "negative_false_flags.jsonl", negatives)
    files["combined.jsonl"] = write_jsonl(out / "combined.jsonl", rows)
    for split in SPLITS:
        files[f"splits/{split}.jsonl"] = write_jsonl(
            out / "splits" / f"{split}.jsonl", by_split[split])

    per_split = {}
    for split in SPLITS:
        part = by_split[split]
        per_split[split] = {
            "total": len(part),
            "positives": sum(1 for r in part if r["is_transaction"]),
            "negatives": sum(1 for r in part if not r["is_transaction"]),
            "templates": len({r["template_id"] for r in part}),
            "merchants": len({r["merchant_id"] for r in part if r["merchant_id"]}),
            "unique_texts": len({r["text"] for r in part}),
        }

    manifest = {
        "dataset": "cashbuddy_gate_synthetic",
        "dataset_version": DATASET_VERSION,
        "schema_version": SCHEMA_VERSION,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "seed": args.seed,
        "generator": "trainingdata/generate_dataset.py",
        "template_versions": {"positive": POS_VERSION, "negative": NEG_VERSION},
        "counts": {
            "total": len(rows),
            "positives": len(positives),
            "negatives": len(negatives),
            "by_split": per_split,
            "positives_by_category": dict(sorted(
                Counter(r["category"] for r in positives).items())),
            "negatives_by_type": dict(sorted(
                Counter(r["negative_type"] for r in negatives).items())),
            "by_source_type": dict(sorted(
                Counter(r["source_type"] for r in rows).items())),
            "by_difficulty": dict(sorted(
                Counter(r["difficulty"] for r in rows).items())),
        },
        "quality": {
            "unique_texts": len(used_texts),
            "duplicate_groups": len({r["event_group_id"] for r in rows if r["event_group_id"]}),
            "duplicate_companions": sum(1 for r in rows if r["duplicate_of"]),
        },
        "files": files,
    }
    (out / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate CashBuddy synthetic gate data")
    parser.add_argument("--positives", type=int, default=20000)
    parser.add_argument("--negatives", type=int, default=20000)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=HERE)
    args = parser.parse_args()
    manifest = generate(args)
    counts = manifest["counts"]
    print(f"Wrote {counts['total']} rows to {args.out}")
    print(f"  positives: {counts['positives']}")
    print(f"  negatives: {counts['negatives']}")
    for split, info in counts["by_split"].items():
        print(f"  {split}: {info['total']} "
              f"({info['positives']} pos / {info['negatives']} neg)")
    print(f"  unique texts: {manifest['quality']['unique_texts']}")
    print(f"  duplicate groups: {manifest['quality']['duplicate_groups']}")


if __name__ == "__main__":
    main()

