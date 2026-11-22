"""Build one offline-capable HTML, without npm/CDN or runtime dependencies."""
from pathlib import Path
import hashlib, base64

# UTF-8 everywhere: with the Windows default code page the CSP hash would not match the served bytes.
UTF8 = "utf-8"
root = Path(__file__).resolve().parent
core = (root / "core.mjs").read_text(encoding=UTF8).replace("export ", "")
app = (root / "app.mjs").read_text(encoding=UTF8).split("\n", 1)[1]
script = core + "\n" + app
digest = base64.b64encode(hashlib.sha256(script.encode(UTF8)).digest()).decode()
csp = "default-src 'none'; script-src 'sha256-" + digest + "'; style-src 'unsafe-inline'; img-src data:; connect-src 'self' https:; base-uri 'none'; form-action 'self'"
html = ((root / "index.template.html").read_text(encoding=UTF8)
        .replace("__CSS__", (root / "style.css").read_text(encoding=UTF8))
        .replace("__SCRIPT__", script).replace("__CSP__", csp))
target = root / "Ubicora.html"
target.write_text(html, encoding=UTF8, newline="\n")
served = root.parent / "server" / "web" / "Ubicora.html"
served.parent.mkdir(exist_ok=True)
served.write_text(html, encoding=UTF8, newline="\n")
print(target)
