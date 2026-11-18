"""Deterministic formatting helpers for the CashBuddy synthetic dataset."""

from __future__ import annotations

import math
import random
from datetime import datetime, timedelta

BASE_DATE = datetime(2026, 11, 18, 21, 0, 0)

AMOUNT_RANGES = {
    "Food & Dining": (49, 2500),
    "Transportation": (20, 3000),
    "Shopping": (99, 25000),
    "Bills & Utilities": (99, 5000),
    "Entertainment": (49, 2000),
    "Healthcare": (100, 20000),
    "Education": (499, 50000),
    "Housing": (1000, 60000),
    "Insurance": (199, 30000),
    "Investments": (100, 100000),
    "Salary": (15000, 200000),
    "Refund": (49, 25000),
    "Gift": (10, 5000),
    "Investment Returns": (100, 50000),
    "Uncategorized": (10, 20000),
}


def group_indian(digits: str) -> str:
    """Group an integer digit string the Indian way: 1234567 -> 12,34,567."""
    if len(digits) <= 3:
        return digits
    head, tail = digits[:-3], digits[-3:]
    parts: list[str] = []
    while len(head) > 2:
        parts.insert(0, head[-2:])
        head = head[:-2]
    if head:
        parts.insert(0, head)
    return ",".join(parts + [tail])


def amount_to_text(rng: random.Random, amount: float) -> tuple[float, str]:
    """Return a normalized amount and the exact string to place in the text."""
    if rng.random() < 0.28:
        amount = round(float(amount), 2)
        whole, frac = f"{amount:.2f}".split(".")
        text = f"{group_indian(whole)}.{frac}"
    else:
        amount = float(int(round(amount)))
        text = group_indian(str(int(amount)))
    if rng.random() < 0.06:
        text = text.replace(",", "")
    return amount, text


def make_amount(rng: random.Random, category: str | None = None,
                bounds: tuple[float, float] | None = None) -> tuple[float, str]:
    lo, hi = bounds or AMOUNT_RANGES.get(category or "", (10, 50000))
    r = rng.random()
    if r < 0.07:
        target = rng.choice([4999.0, 5000.0, 9999.0, 10000.0, 10001.0, 25000.0, 100000.0])
        amount = min(max(target, lo), hi)
    elif r < 0.15:
        amount = float(rng.randint(int(lo), min(int(hi), int(lo) + 500)))
    else:
        amount = math.exp(rng.uniform(math.log(max(lo, 1)), math.log(max(hi, lo + 1))))
    return amount_to_text(rng, amount)


def make_datetime(rng: random.Random, days: int = 90) -> datetime:
    return BASE_DATE - timedelta(minutes=rng.randint(0, days * 24 * 60))


def date_text(rng: random.Random, dt: datetime) -> str:
    pattern = rng.choice([
        "%d-%m-%y", "%d/%m/%Y", "%d %b %Y", "%d-%b-%y",
        "%d.%m.%Y", "%d %B %Y",
    ])
    return dt.strftime(pattern)


def time_text(rng: random.Random, dt: datetime) -> str:
    if rng.random() < 0.3:
        return dt.strftime("%I:%M %p").lstrip("0")
    return dt.strftime("%H:%M")


def long_datetime_text(rng: random.Random, dt: datetime) -> str:
    txt = dt.strftime("%d %b %Y, %I:%M %p").replace("AM", "am").replace("PM", "pm")
    return txt.lstrip("0")


def month_text(rng: random.Random, dt: datetime) -> str:
    return dt.strftime("%B %Y") if rng.random() < 0.5 else dt.strftime("%b %Y")


def make_acct(rng: random.Random) -> str:
    return f"{rng.randint(0, 9999):04d}"


def make_utr(rng: random.Random) -> str:
    return str(rng.randint(10**11, 10**12 - 1))


def make_otp(rng: random.Random) -> str:
    return f"{rng.randint(0, 999999):06d}"


def make_safe_phone(rng: random.Random) -> str:
    # Reserved-for-fiction style tail only. Never a real subscriber number.
    return str(rng.randint(100000, 999999))


def make_vpa(rng: random.Random, merchant_name: str) -> str:
    slug = "".join(ch for ch in merchant_name.lower() if ch.isalnum())[:14] or "merchant"
    handle = rng.choice([
        "@okaxis", "@okhdfcbank", "@okicici", "@oksbi", "@ybl",
        "@paytm", "@upi", "@axl", "@ibl", "@airtel",
    ])
    if rng.random() < 0.35:
        slug = f"{slug}{rng.randint(1, 99)}"
    return f"{slug}{handle}"
