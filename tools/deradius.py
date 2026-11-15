import re
import pathlib

PAT = re.compile(r"border-radius:\s*([^;]+);")


def fix(m):
    val = m.group(1).strip()
    if "50%" in val:
        return m.group(0)
    if val.startswith("999px"):
        return "border-radius: 2px;"
    toks = re.findall(r"(\d+(?:\.\d+)?)px", val)
    if toks and any(float(t) >= 6 for t in toks):
        return "border-radius: 0;"
    if toks:
        return "border-radius: 2px;"
    return m.group(0)


for name in ["app.css", "styles.css"]:
    p = pathlib.Path(name)
    s = p.read_text(encoding="utf-8")
    s2 = PAT.sub(fix, s)
    s2 = s2.replace("--radius: 10px;", "--radius: 0;")
    p.write_text(s2, encoding="utf-8")
    print(name, "changed:", s.count("border-radius:") , "->", s2.count("border-radius:"))

for name in ["app.css", "styles.css"]:
    s = pathlib.Path(name).read_text(encoding="utf-8")
    left = sorted(set(re.findall(r"border-radius:\s*[^;]+;", s)))
    big = [x for x in left if re.search(r"([6-9]|\d\d)px", x) or "999px" in x]
    print(name, "剩余大圆角:", big)
