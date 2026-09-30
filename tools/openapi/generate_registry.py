#!/usr/bin/env python3
"""Generate the compact Cloudflare endpoint registry shipped in the APK.

Input:  Cloudflare's official OpenAPI schema (github.com/cloudflare/api-schemas, openapi.json)
Output: app/src/main/assets/cf_endpoints.json   compact registry used by the API Explorer
        docs/API_COVERAGE.md                     human-readable coverage matrix
        build/api-coverage.json                   machine-readable coverage report

Native coverage is derived, not hand-maintained: every Retrofit annotation in
CloudflareApi.kt is normalized and matched against the schema paths.

Usage:
  generate_registry.py --schema openapi.json --revision <git sha> [--previous old_registry.json]

With --previous, the script diffs endpoints and exits with status 2 if an endpoint the app
calls natively has been removed from the schema (a breaking change CI must stop on).
"""
import argparse
import datetime
import gzip
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
API_KT = os.path.join(ROOT, "app/src/main/java/dev/cfmobile/app/data/remote/CloudflareApi.kt")
ASSET = os.path.join(ROOT, "app/src/main/assets/cf_endpoints.json.gz")
COVERAGE_MD = os.path.join(ROOT, "docs/API_COVERAGE.md")
COVERAGE_JSON = os.path.join(ROOT, "build/api-coverage.json")

METHODS = ("get", "post", "put", "patch", "delete")
MAX_DESC = 160
MAX_ENUM = 24


def norm_path(path):
    path = path.strip().lstrip("/").split("?")[0]
    return re.sub(r"\{[^}]+\}", "{}", path)


def native_paths():
    found = set()
    if not os.path.exists(API_KT):
        return found
    src = open(API_KT, encoding="utf-8").read()
    for method, path in re.findall(r'@(GET|POST|PUT|PATCH|DELETE)\("([^"]+)"\)', src):
        found.add((method.lower(), norm_path(path)))
    for method, path in re.findall(r'@HTTP\(method\s*=\s*"(\w+)",\s*path\s*=\s*"([^"]+)"', src):
        found.add((method.lower(), norm_path(path)))
    return found


class Resolver:
    def __init__(self, doc):
        self.doc = doc

    def ref(self, node, seen=()):
        while isinstance(node, dict) and "$ref" in node:
            r = node["$ref"]
            if r in seen:
                return {}
            seen = seen + (r,)
            cur = self.doc
            for part in r.lstrip("#/").split("/"):
                cur = cur.get(part, {})
            node = cur
        return node

    def merged(self, schema, depth=0):
        schema = self.ref(schema)
        if not isinstance(schema, dict) or depth > 6:
            return {}
        if "allOf" in schema:
            out = {"type": "object", "properties": {}, "required": []}
            for part in schema["allOf"]:
                m = self.merged(part, depth + 1)
                out["properties"].update(m.get("properties", {}))
                out["required"] += m.get("required", [])
                for k in ("example", "description"):
                    if k in m and k not in out:
                        out[k] = m[k]
            for k, v in schema.items():
                if k != "allOf":
                    out.setdefault(k, v)
            return out
        for key in ("oneOf", "anyOf"):
            if key in schema and schema[key]:
                first = self.merged(schema[key][0], depth + 1)
                rest = {k: v for k, v in schema.items() if k != key}
                first = dict(first)
                first.update({k: v for k, v in rest.items() if k not in first})
                return first
        return schema

    def template(self, schema, depth=0):
        s = self.merged(schema)
        if depth > 4 or not s:
            return None
        if "example" in s:
            return s["example"]
        if "default" in s:
            return s["default"]
        if s.get("enum"):
            return s["enum"][0]
        t = s.get("type")
        if isinstance(t, list):
            t = next((x for x in t if x != "null"), "string")
        if t == "object" or "properties" in s:
            props = s.get("properties", {})
            required = s.get("required") or []
            keys = [k for k in props if k in required] or list(props)[:8]
            out = {}
            for k in keys:
                v = self.template(props[k], depth + 1)
                out[k] = v if v is not None else ""
            return out
        if t == "array":
            item = self.template(s.get("items", {}), depth + 1)
            return [] if item is None else [item]
        if t == "boolean":
            return False
        if t in ("integer", "number"):
            return 0
        return ""


def short(text):
    if not text:
        return ""
    text = re.sub(r"\s+", " ", str(text)).strip()
    return text if len(text) <= MAX_DESC else text[: MAX_DESC - 1].rstrip() + "..."


def param_entry(res, p):
    p = res.ref(p)
    schema = res.merged(p.get("schema", {}))
    t = schema.get("type", "string")
    if isinstance(t, list):
        t = next((x for x in t if x != "null"), "string")
    entry = {"n": p.get("name"), "in": p.get("in"), "t": t}
    if p.get("required"):
        entry["r"] = 1
    enum = schema.get("enum")
    if enum:
        entry["e"] = [str(x) for x in enum[:MAX_ENUM]]
    d = short(p.get("description") or schema.get("description"))
    if d:
        entry["d"] = d
    return entry


def body_entry(res, op):
    rb = res.ref(op.get("requestBody", {}))
    content = rb.get("content", {})
    if not content:
        return None
    ctype = next(iter(content))
    media = content[ctype]
    example = None
    if media.get("examples"):
        first = res.ref(next(iter(media["examples"].values())))
        example = first.get("value")
    if example is None and "example" in media:
        example = media["example"]
    if example is None and ctype.endswith("json"):
        example = res.template(media.get("schema", {}))
    out = {"ct": ctype}
    if example is not None and ctype.endswith("json"):
        out["ex"] = example
    return out


def build(doc, revision):
    res = Resolver(doc)
    native = native_paths()
    endpoints = []
    for path, item in doc["paths"].items():
        shared = item.get("parameters", [])
        for method in METHODS:
            op = item.get(method)
            if not isinstance(op, dict) or op.get("x-forge-hidden") is True and not op.get("summary"):
                continue
            params = [param_entry(res, p) for p in shared + op.get("parameters", [])]
            params = [p for p in params if p.get("in") in ("path", "query")]
            e = {
                "id": op.get("operationId") or f"{method}:{path}",
                "m": method.upper(),
                "p": path.lstrip("/"),
                "s": short(op.get("summary")) or path,
                "g": (op.get("tags") or ["Other"])[0],
            }
            d = short(op.get("description"))
            if d and d != e["s"]:
                e["d"] = d
            perms = op.get("x-api-token-group")
            if perms:
                e["perm"] = perms
            plans = op.get("x-cfPlanAvailability")
            if isinstance(plans, dict):
                avail = [k for k, v in plans.items() if v]
                if avail and len(avail) < len(plans):
                    e["plan"] = avail
            if op.get("deprecated"):
                e["dep"] = 1
            if params:
                e["q"] = params
            body = body_entry(res, op)
            if body:
                e["b"] = body
            if (method, norm_path(path)) in native:
                e["n"] = 1
            endpoints.append(e)
    endpoints.sort(key=lambda x: (x["g"].lower(), x["p"], x["m"]))
    return {
        "schemaRevision": revision,
        "apiVersion": doc.get("info", {}).get("version", ""),
        "generatedAt": os.environ.get("REGISTRY_GENERATED_AT") or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "endpoints": endpoints,
    }


def diff(prev, cur):
    key = lambda e: (e["m"], e["p"])
    old = {key(e): e for e in prev.get("endpoints", [])}
    new = {key(e): e for e in cur["endpoints"]}
    added = sorted(set(new) - set(old))
    removed = sorted(set(old) - set(new))
    changed = []
    for k in sorted(set(old) & set(new)):
        a, b = old[k], new[k]
        req_a = {p["n"] for p in a.get("q", []) if p.get("r")}
        req_b = {p["n"] for p in b.get("q", []) if p.get("r")}
        notes = []
        if req_b - req_a:
            notes.append("new required params: " + ", ".join(sorted(req_b - req_a)))
        if a.get("perm") != b.get("perm"):
            notes.append(f"permissions {a.get('perm')} -> {b.get('perm')}")
        if not a.get("dep") and b.get("dep"):
            notes.append("now deprecated")
        if notes:
            changed.append((k, notes, bool(a.get("n"))))
    breaking = [k for k in removed if old[k].get("n")] + [k for k, n, native in changed if native and any("required" in x for x in n)]
    return added, removed, changed, breaking


def write_coverage(reg):
    eps = reg["endpoints"]
    native = [e for e in eps if e.get("n")]
    groups = {}
    for e in eps:
        g = groups.setdefault(e["g"], [0, 0])
        g[0] += 1
        g[1] += 1 if e.get("n") else 0
    report = {
        "schemaRevision": reg["schemaRevision"],
        "generatedAt": reg["generatedAt"],
        "total": len(eps),
        "native": len(native),
        "generic": len(eps) - len(native),
        "deprecated": sum(1 for e in eps if e.get("dep")),
        "groups": {k: {"total": v[0], "native": v[1]} for k, v in sorted(groups.items())},
    }
    os.makedirs(os.path.dirname(COVERAGE_JSON), exist_ok=True)
    json.dump(report, open(COVERAGE_JSON, "w"), indent=1)
    lines = [
        "# API Coverage",
        "",
        "Generated by `tools/openapi/generate_registry.py` from Cloudflare's official OpenAPI schema.",
        "Do not edit by hand.",
        "",
        f"- Schema revision: `{reg['schemaRevision']}`",
        f"- Generated: {reg['generatedAt']}",
        f"- Endpoints in schema: {report['total']}",
        f"- Native UI: {report['native']}",
        f"- Generic API (API Explorer and All Cloudflare APIs): {report['generic']}",
        f"- Marked deprecated by Cloudflare: {report['deprecated']}",
        "",
        "Every endpoint in the schema is reachable from the in-app API Explorer, constrained by",
        "the active token. \"Native\" means a dedicated screen calls that exact method and path.",
        "",
        "| Product group | Endpoints | Native | Coverage |",
        "| --- | ---: | ---: | --- |",
    ]
    for g, v in sorted(groups.items(), key=lambda kv: (-kv[1][1], kv[0].lower())):
        pct = int(round(100 * v[1] / v[0])) if v[0] else 0
        label = "Native" if pct == 100 else ("Native + Generic API" if v[1] else "Generic API")
        lines.append(f"| {g} | {v[0]} | {v[1]} | {label} ({pct}%) |")
    os.makedirs(os.path.dirname(COVERAGE_MD), exist_ok=True)
    open(COVERAGE_MD, "w").write("\n".join(lines) + "\n")
    return report


def load_registry(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--schema", required=True)
    ap.add_argument("--revision", default="unknown")
    ap.add_argument("--previous")
    args = ap.parse_args()
    doc = json.load(open(args.schema, encoding="utf-8"))
    reg = build(doc, args.revision)
    exit_code = 0
    if args.previous and os.path.exists(args.previous):
        added, removed, changed, breaking = diff(load_registry(args.previous), reg)
        print(f"added={len(added)} removed={len(removed)} changed={len(changed)} breaking={len(breaking)}")
        for k in added[:200]:
            print("  + ", *k)
        for k in removed:
            print("  - ", *k)
        for k, notes, _ in changed:
            print("  ~ ", *k, "; ".join(notes))
        if breaking:
            print("BREAKING changes affect natively implemented endpoints:")
            for k in breaking:
                print("  ! ", *k)
            exit_code = 2
    os.makedirs(os.path.dirname(ASSET), exist_ok=True)
    data = json.dumps(reg, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    # mtime=0 keeps the asset byte-identical across runs with the same schema (reproducible builds).
    with open(ASSET, "wb") as f:
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(data)
    report = write_coverage(reg)
    print(f"endpoints={report['total']} native={report['native']} size={os.path.getsize(ASSET)} bytes")
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
