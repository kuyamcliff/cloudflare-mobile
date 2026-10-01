#!/usr/bin/env python3
"""Fails CI if anything that looks like a credential is tracked in git (spec 288).

Scans every tracked text file for Cloudflare API tokens, legacy Global API keys, R2/S3
secret keys, private keys and keystores. Test fixtures use obviously fake values that do not
match these shapes.
"""
import re
import subprocess
import sys

PATTERNS = {
    "Cloudflare account token": re.compile(r"\bcfat_[A-Za-z0-9_-]{30,}"),
    "Cloudflare user token": re.compile(r"\bcfut_[A-Za-z0-9_-]{30,}"),
    "Bearer token literal": re.compile(r"Bearer\s+[A-Za-z0-9_-]{40}\b"),
    "Global API key header": re.compile(r"X-Auth-Key[\"']?\s*[:=]\s*[\"']?[a-f0-9]{37}\b", re.I),
    "Private key": re.compile(r"-----BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY-----"),
    "AWS-style secret": re.compile(r"(?i)secret[_-]?access[_-]?key[\"']?\s*[:=]\s*[\"'][A-Za-z0-9/+]{40}[\"']"),
    "R2 secret (64 hex)": re.compile(r"(?i)r2[_-]?secret[\"']?\s*[:=]\s*[\"'][a-f0-9]{64}[\"']"),
}
FORBIDDEN_FILES = re.compile(r"\.(jks|keystore|p12|pem)$|(^|/)keystore\.properties$")
# AWS's own documentation example key, used by the SigV4 test vector.
ALLOW = {"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"}


def main() -> int:
    files = subprocess.run(["git", "ls-files"], capture_output=True, text=True, check=True).stdout.split()
    problems = []
    for path in files:
        if FORBIDDEN_FILES.search(path):
            problems.append(f"{path}: credential file must not be committed")
            continue
        if path.endswith((".gz", ".png", ".jar", ".webp", ".jpg")):
            continue
        try:
            text = open(path, encoding="utf-8").read()
        except (UnicodeDecodeError, FileNotFoundError):
            continue
        for name, pattern in PATTERNS.items():
            for m in pattern.finditer(text):
                if any(a in m.group(0) for a in ALLOW):
                    continue
                line = text.count("\n", 0, m.start()) + 1
                problems.append(f"{path}:{line}: possible {name}")
    for p in problems:
        print(p)
    print(f"secret scan: {len(files)} files, {len(problems)} findings")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
