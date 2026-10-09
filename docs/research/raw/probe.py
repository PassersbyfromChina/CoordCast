import urllib.request
cands = [
 'https://developer.apple.com/tutorials/data/design/human-interface-guidelines/typography.json',
 'https://developer.apple.com/tutorials/data/design/human-interface-guidelines/materials.json',
 'https://developer.apple.com/tutorials/data/documentation/design/human-interface-guidelines.json',
 'https://developer.apple.com/design/human-interface-guidelines/typography.json',
]
for u in cands:
    try:
        req = urllib.request.Request(u, headers={'User-Agent':'Mozilla/5.0'})
        r = urllib.request.urlopen(req, timeout=45)
        b = r.read()
        print('OK', u, len(b))
    except Exception as e:
        print('FAIL', u, e)
