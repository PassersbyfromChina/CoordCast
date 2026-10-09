import re, zlib, sys
data = open(r'old_uri.pdf','rb').read()
texts = []
for m in re.finditer(rb'stream\r?\n', data):
    start = m.end()
    end = data.find(b'endstream', start)
    if end < 0: continue
    raw = data[start:end]
    try:
        d = zlib.decompress(raw)
    except Exception:
        continue
    if b'Tj' not in d and b'TJ' not in d: continue
    try:
        s = d.decode('latin-1')
    except Exception:
        continue
    texts.append(s)
print("streams with text:", len(texts))
out = []
for s in texts:
    for mm in re.finditer(r'\((?:\\.|[^()\\])*\)', s):
        t = mm.group(0)[1:-1]
        t = t.replace('\\(', '(').replace('\\)', ')').replace('\\\\', '\\')
        out.append(t)
txt = ' '.join(out)
open('old_uri.txt','w',encoding='utf-8').write(txt)
print("chars:", len(txt))
print(txt[:1500])
