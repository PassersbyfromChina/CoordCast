import json, urllib.request
def walk(node, out):
    if isinstance(node, dict):
        t = node.get('type')
        if t == 'text' and 'text' in node: out.append(node['text'])
        elif t == 'codeVoice' and 'code' in node: out.append('`'+node['code']+'`')
        elif t == 'heading': out.append('\n## '+node.get('text','')+'\n')
        for k,v in node.items():
            if k in ('text','code','title','identifier'): continue
            walk(v, out)
    elif isinstance(node, list):
        for x in node: walk(x, out)
urls = [
 ('uikit_controlsize','https://developer.apple.com/tutorials/data/documentation/uikit/uicontrol/controlsize.json'),
 ('swiftui_controlsize','https://developer.apple.com/tutorials/data/documentation/swiftui/controlsize.json'),
]
for name,u in urls:
    try:
        req = urllib.request.Request(u, headers={'User-Agent':'Mozilla/5.0'})
        d = json.loads(urllib.request.urlopen(req, timeout=45).read().decode('utf-8-sig'))
        out=[]; walk(d.get('primaryContentSections', d), out)
        print('=== ', name, ' ===')
        print(''.join(out)[:2500])
    except Exception as e:
        print('FAIL', name, e)
