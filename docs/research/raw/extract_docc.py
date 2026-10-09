import json, sys, re
def walk(node, out):
    if isinstance(node, dict):
        t = node.get('type')
        if t == 'text' and 'text' in node:
            out.append(node['text'])
        elif t == 'codeVoice' and 'code' in node:
            out.append('`'+node['code']+'`')
        elif t == 'reference':
            out.append(node.get('title') or node.get('identifier',''))
        elif t == 'heading':
            out.append('\n\n## '+node.get('text','')+'\n')
        elif t == 'codeListing':
            out.append('\n```\n'+'\n'.join(node.get('code',[]))+'\n```\n')
        for k,v in node.items():
            if k in ('text','code','title','identifier'): continue
            walk(v, out)
    elif isinstance(node, list):
        for x in node: walk(x, out)
for path,outp in [('lg_https___developer_apple_com_tutorials_data_documentation_technologyoverviews_liquid_glass_json.json','lg_liquid_glass.txt'),
                  ('lg_https___developer_apple_com_tutorials_data_documentation_TechnologyOverviews_liquid_glass_json.json','lg_liquid_glass2.txt'),
                  ('lg_https___developer_apple_com_tutorials_data_documentation_technologyoverviews_adopting_liquid_glass_json.json','lg_adopting.txt')]:
    try:
        d = json.load(open(path, encoding='utf-8-sig'))
    except Exception as e:
        print('ERR', path, e); continue
    out = []
    walk(d.get('primaryContentSections', d), out)
    txt = re.sub(r'\n{3,}','\n\n',' '.join(out) if out and len(out)<5 else ''.join(out))
    open(outp,'w',encoding='utf-8-sig').write(txt)
    print(outp, len(txt))

