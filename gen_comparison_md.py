import os, re, glob

races_dir = r'E:/Hytale/Hytale-Mods/endlessleveling/src/main/resources/races'
ATTR_NAMES = ['life_force','strength','defense','haste','precision','ferocity','stamina','flow','sorcery','discipline']
ONE_DECIMAL = {'life_force','flow','stamina','discipline'}

def parse_file(path):
    data = {}
    with open(path, encoding='utf-8') as f:
        content = f.read()
    lines = content.splitlines()
    for line in lines:
        m = re.match(r'^\s+id:\s*["\']?(\w+)["\']?', line)
        if m: data['id'] = m.group(1); break
    for line in lines:
        m = re.match(r'^\s*stage:\s*["\']?(\w+)["\']?', line)
        if m: data['stage'] = m.group(1); break
    for line in lines:
        m = re.match(r'^\s*path:\s*["\']?(\w+)["\']?', line)
        if m: data['path'] = m.group(1); break
    attrs = {}
    in_attrs = False
    for line in lines:
        if re.match(r'^attributes\s*:', line):
            in_attrs = True; continue
        if in_attrs:
            for a in ATTR_NAMES:
                m2 = re.match(r'^\s+' + a + r':\s*([\d.]+)', line)
                if m2: attrs[a] = float(m2.group(1))
            if re.match(r'^\S', line) and 'attributes' not in line:
                in_attrs = False
    data['attrs'] = attrs
    block = re.search(r'required_any_forms:(.*?)(?=\n\w|\Z)', content, re.DOTALL)
    if block:
        pids = re.findall(r'\bid:\s*["\']?(\w+)["\']?', block.group(1))
        if pids: data['required_any_forms'] = pids
    return data

all_files = glob.glob(os.path.join(races_dir, '**', '*.yml'), recursive=True)
forms = {}
for fp in all_files:
    d = parse_file(fp)
    if 'id' in d and d['attrs']:
        forms[d['id']] = d

# Build predecessor map
pred_map = {}
for fid in forms:
    fp_list = glob.glob(os.path.join(races_dir, '**', fid + '.yml'), recursive=True)
    if not fp_list: continue
    with open(fp_list[0], encoding='utf-8') as f:
        content = f.read()
    np_block = re.search(r'next_paths:(.*?)(?=\n[a-z_]|\Z)', content, re.DOTALL)
    if np_block:
        ids_in_np = re.findall(r'\bid:\s*["\']?(\w+)["\']?', np_block.group(1))
        for nid in ids_in_np:
            pred_map.setdefault(nid, []).append(fid)

def compute_original(form_id, visited=None):
    if visited is None: visited = set()
    if form_id in visited: return None
    visited.add(form_id)
    form = forms.get(form_id)
    if not form: return None
    stage = form.get('stage')
    if stage == 'base':
        return form['attrs'].copy()
    preds = form.get('required_any_forms')
    if stage == 'final' and preds:
        parent_attrs = [compute_original(pid, visited.copy()) for pid in preds if pid in forms]
        parent_attrs = [pa for pa in parent_attrs if pa]
        if not parent_attrs: return None
        result = {}
        for a in ATTR_NAMES:
            best = max(pv.get(a, 0) for pv in parent_attrs)
            result[a] = best * 1.30
        return result
    parent_ids = pred_map.get(form_id, [])
    if not parent_ids: return None
    parent_orig = compute_original(parent_ids[0], visited.copy())
    if parent_orig is None: return None
    return {a: parent_orig.get(a, 0) * 1.10 for a in ATTR_NAMES}

def fmt(val, attr):
    if val is None: return 'N/A'
    return f"{val:.1f}" if attr in ONE_DECIMAL else f"{val:.2f}"

def pct(orig, curr):
    if orig is None or orig == 0: return ''
    d = (curr - orig) / orig * 100
    return f"{'+' if d >= 0 else ''}{d:.0f}%"

# ─── OUTPUT MARKDOWN ─────────────────────────────────────────────────────────
out = []
out.append("# Race Attribute Comparison: Original (Flat 10/10/30) vs Current (Phase 4)\n")
out.append("> **Original**: Every stat scaled by ×1.10 at tier_1, ×1.10 at tier_2, best_parent×1.30 at final — no path differentiation.\n")
out.append("> **Current**: Per-path, per-stat scaling with primary/secondary split, health boost, precision/ferocity always ×1.20/1.20/1.60, defense-path health bonus.\n\n")
out.append("---\n")

DISPLAY_ATTRS = ['life_force','strength','sorcery','defense','haste','precision','ferocity','stamina','flow']
COL_LABELS = {'life_force':'HP','strength':'STR','sorcery':'SORC','defense':'DEF','haste':'HASTE','precision':'PREC','ferocity':'FER','stamina':'STAM','flow':'FLOW'}

stage_order = {'base': 0, 'tier_1': 1, 'tier_2': 2, 'final': 3}

bases = {k: v for k, v in forms.items() if v.get('stage') == 'base'}
for base_id in sorted(bases.keys()):
    race_forms = {k: v for k, v in forms.items()
                  if k == base_id or k.startswith(base_id + '_')}
    if len(race_forms) < 2:
        continue
    base_attrs = bases[base_id]['attrs']
    sorted_forms = sorted(race_forms.items(),
                          key=lambda x: (stage_order.get(x[1].get('stage',''), 9), x[0]))

    out.append(f"## {base_id.replace('_',' ').title()}\n")
    out.append(f"**Base** — " + "  |  ".join(f"{COL_LABELS[a]}: {fmt(base_attrs.get(a), a)}" for a in DISPLAY_ATTRS) + "\n\n")

    # Table header
    col_w = 22
    header = f"| {'Form / Path':<28} | {'Stage':<6} |"
    for a in DISPLAY_ATTRS:
        header += f" {COL_LABELS[a]:^{col_w}} |"
    sep = f"| {'-'*28} | {'-'*6} |" + "".join(f" {'-'*col_w} |" for _ in DISPLAY_ATTRS)
    out.append(header + "\n")
    out.append(sep + "\n")

    for fid, fd in sorted_forms:
        stage = fd.get('stage', '?')
        path = fd.get('path', 'none')
        curr = fd['attrs']
        if stage == 'base':
            continue
        orig = compute_original(fid)
        row_parts = []
        for a in DISPLAY_ATTRS:
            cv = curr.get(a)
            ov = orig.get(a) if orig else None
            if cv is None and ov is None:
                cell = f"{'—':^{col_w}}"
            elif ov is None:
                cell = f"{'NOW '+fmt(cv,a):^{col_w}}"
            else:
                diff = pct(ov, cv)
                cell = f"{'→'.join([fmt(ov,a), fmt(cv,a)])+' '+diff:^{col_w}}"
            row_parts.append(cell)
        label = f"{fid.replace(base_id+'_','').replace('_',' ')} ({path})"
        out.append(f"| {label:<28} | {stage:<6} |" + "".join(f" {p} |" for p in row_parts) + "\n")

    out.append("\n")

with open('comparison_chart.md', 'w', encoding='utf-8') as f:
    f.writelines(out)
print("Done — written to comparison_chart.md")
