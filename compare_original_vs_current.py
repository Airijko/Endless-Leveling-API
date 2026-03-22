import os, re, glob

races_dir = r'E:/Hytale/Hytale-Mods/endlessleveling/src/main/resources/races'
ATTR_NAMES = ['life_force','strength','defense','haste','precision','ferocity','stamina','flow','sorcery','discipline']
ONE_DECIMAL = {'life_force','flow','stamina','discipline'}

# ─── PATH FAMILY + PRIMARY (same as scaling script) ─────────────────────────
PATH_FAMILY = {
    'damage':'damage','strength':'damage','sorcery':'damage','offense':'damage','crit':'damage',
    'defense':'defense','tank':'defense',
    'support':'support','flow':'support',
    'adventurer':'adventurer','evasion':'evasion',
}
PRIMARY = {
    'damage':     {'strength','sorcery'},
    'defense':    {'defense','life_force'},
    'support':    {'stamina','flow'},
    'adventurer': {'stamina','flow','life_force'},
    'evasion':    {'haste','stamina'},
}
HIGH_RESOURCE_PATHS = {'support','adventurer','evasion'}

# Phase 4 (current) profiles  (t1_mult, t2_mult, final_mult) applied per stage
PROFILE_HEALTH_DEFAULT = (1.30, 1.30, 1.90)
PROFILE_HEALTH_DEFENSE = (1.35, 1.35, 2.05)
PROFILE_OFFENSE        = (1.10, 1.10, 1.20)
PROFILE_DEFENSE        = (1.15, 1.15, 1.45)
PROFILE_RATIO          = (1.20, 1.20, 1.60)
PROFILE_GENERIC        = (1.10, 1.10, 1.20)

def half_profile(p):
    return tuple(1.0 + (x - 1.0) / 2.0 for x in p)

def base_profile(attr, fam):
    if attr == 'life_force':
        return PROFILE_HEALTH_DEFENSE if fam == 'defense' else PROFILE_HEALTH_DEFAULT
    if attr in ('precision','ferocity'):
        return PROFILE_RATIO
    if attr in ('stamina','flow'):
        return PROFILE_RATIO if fam in HIGH_RESOURCE_PATHS else PROFILE_GENERIC
    if attr in ('strength','sorcery'):
        return PROFILE_OFFENSE
    if attr == 'defense':
        return PROFILE_DEFENSE
    return PROFILE_GENERIC

def multiplier(attr, fam, stage_idx):
    """stage_idx: 0=tier_1, 1=tier_2, 2=final"""
    bp = base_profile(attr, fam)
    prims = PRIMARY.get(fam, set())
    if attr in ('life_force','precision','ferocity') or attr in prims:
        return bp[stage_idx]
    return half_profile(bp)[stage_idx]

# ─── FILE PARSER ─────────────────────────────────────────────────────────────
def parse_file(path):
    data = {}
    with open(path, encoding='utf-8') as f:
        content = f.read()
    lines = content.splitlines()

    # id is the FIRST indented id: line (under ascension:)
    for line in lines:
        m = re.match(r'^\s+id:\s*["\']?(\w+)["\']?', line)
        if m:
            data['id'] = m.group(1)
            break

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

# ─── LOAD ALL ────────────────────────────────────────────────────────────────
all_files = glob.glob(os.path.join(races_dir, '**', '*.yml'), recursive=True)
forms = {}
for fp in all_files:
    d = parse_file(fp)
    if 'id' in d and d['attrs']:
        forms[d['id']] = d

bases = {k: v for k, v in forms.items() if v.get('stage') == 'base'}
print(f"Loaded {len(forms)} forms, {len(bases)} bases\n")

# ─── ORIGINAL FLAT SYSTEM: tier_1 x1.10, tier_2 x1.10, final x1.30 ─────────
def compute_original(form_id):
    """Compute what attributes would be under the original flat 10/10/30 system."""
    form = forms[form_id]
    stage = form.get('stage')
    if stage == 'base':
        return form['attrs'].copy()

    preds = form.get('required_any_forms', [])
    if not preds:
        # single parent: find via next_paths linkage (fall back to same-race base propagation)
        # For non-final, predecessor is implied by looking at who points here
        # We'll recursively resolve: tier_1 from base, tier_2 from tier_1
        # Heuristic: strip last trailing word to find predecessor
        # Instead just use the flat multiplier on base
        pass

    if stage == 'final':
        parent_vals = []
        for pid in preds:
            if pid in forms:
                pv = compute_original(pid)
                parent_vals.append(pv)
        if not parent_vals:
            return form['attrs'].copy()
        result = {}
        for a in ATTR_NAMES:
            best = max(pv.get(a, 0) for pv in parent_vals)
            result[a] = best * 1.30
        return result
    else:
        # tier_1 or tier_2: use predecessor chain
        # We need to find the actual predecessor
        return None  # handled below

def find_predecessors(form_id):
    """Find which forms list this form_id in their next_paths."""
    targets = []
    for fid, fd in forms.items():
        content_path = os.path.join(races_dir, '**', fid + '.yml')
        files = glob.glob(content_path, recursive=True)
        if not files: continue
        with open(files[0], encoding='utf-8') as f:
            content = f.read()
        # Check next_paths section
        np_block = re.search(r'next_paths:(.*?)(?=\n[a-z]|\Z)', content, re.DOTALL)
        if np_block:
            ids_in_np = re.findall(r'\bid:\s*["\']?(\w+)["\']?', np_block.group(1))
            if form_id in ids_in_np:
                targets.append(fid)
    return targets

# Build predecessor map from next_paths
print("Building predecessor map...")
pred_map = {}  # form_id -> list of forms that lead TO it
for fid, fd in forms.items():
    fp_list = glob.glob(os.path.join(races_dir, '**', fid + '.yml'), recursive=True)
    if not fp_list: continue
    with open(fp_list[0], encoding='utf-8') as f:
        content = f.read()
    np_block = re.search(r'next_paths:(.*?)(?=\n[a-z_]|\Z)', content, re.DOTALL)
    if np_block:
        ids_in_np = re.findall(r'\bid:\s*["\']?(\w+)["\']?', np_block.group(1))
        for nid in ids_in_np:
            pred_map.setdefault(nid, []).append(fid)

print(f"Predecessor map built for {len(pred_map)} forms\n")

def get_base_attrs_for_form(form_id):
    """Walk back through predecessors to find the base form's attrs."""
    visited = set()
    current = form_id
    while current in forms and forms[current].get('stage') != 'base':
        if current in visited:
            break
        visited.add(current)
        preds = pred_map.get(current, [])
        if not preds:
            # check required_any_forms
            req = forms[current].get('required_any_forms', [])
            if req:
                preds = req
        if preds:
            current = preds[0]
        else:
            break
    if current in forms and forms[current].get('stage') == 'base':
        return forms[current]['attrs']
    return None

def compute_original_chained(form_id):
    """Compute original flat values by walking the predecessor chain."""
    form = forms[form_id]
    stage = form.get('stage')
    if stage == 'base':
        return form['attrs'].copy()

    preds = forms[form_id].get('required_any_forms')
    if stage == 'final' and preds:
        parent_attrs = []
        for pid in preds:
            if pid in forms:
                pa = compute_original_chained(pid)
                parent_attrs.append(pa)
        if not parent_attrs:
            return None
        result = {}
        for a in ATTR_NAMES:
            best = max(pv.get(a, 0) for pv in parent_attrs)
            result[a] = best * 1.30
        return result

    # tier_1 or tier_2 (single parent)
    parent_ids = pred_map.get(form_id, [])
    if not parent_ids:
        return None
    parent_id = parent_ids[0]
    parent_orig = compute_original_chained(parent_id)
    if parent_orig is None:
        return None
    result = {}
    for a in ATTR_NAMES:
        result[a] = parent_orig.get(a, 0) * 1.10
    return result

# ─── FORMAT ──────────────────────────────────────────────────────────────────
def fmt(val, attr):
    if val is None: return 'N/A'
    if attr in ONE_DECIMAL:
        return f"{val:.1f}"
    return f"{val:.2f}"

def pct_diff(orig, curr):
    if orig is None or orig == 0: return ''
    d = (curr - orig) / orig * 100
    sign = '+' if d >= 0 else ''
    return f"({sign}{d:.0f}%)"

# ─── PRINT COMPARISON TABLE FOR EACH RACE ────────────────────────────────────
# Group non-base forms by race (same prefix)
races_order = sorted(bases.keys())

for base_id in races_order:
    base_form = forms[base_id]
    race_prefix = base_id  # e.g. "human"

    # Find all forms that belong to this race
    race_forms = {k: v for k, v in forms.items()
                  if k == base_id or k.startswith(race_prefix + '_')}
    if len(race_forms) < 2:
        continue

    print(f"{'='*100}")
    print(f"  RACE: {base_id.upper()}")
    print(f"{'='*100}")

    # Sort: base first, then by stage
    stage_order = {'base': 0, 'tier_1': 1, 'tier_2': 2, 'final': 3}
    sorted_forms = sorted(race_forms.items(),
                          key=lambda x: (stage_order.get(x[1].get('stage',''), 9), x[0]))

    header = f"  {'Form':<30} {'Stage':<8} {'Path':<12}"
    for a in ATTR_NAMES:
        header += f"  {a[:7]:>14}"
    print(header)
    print(f"  {'-'*28} {'-'*6} {'-'*10}" + "  " + ("  " + "-"*14) * len(ATTR_NAMES))

    for fid, fd in sorted_forms:
        stage = fd.get('stage', '?')
        path = fd.get('path', 'none')
        curr_attrs = fd['attrs']

        if stage == 'base':
            row = f"  {fid:<30} {stage:<8} {path:<12}"
            for a in ATTR_NAMES:
                row += f"  {fmt(curr_attrs.get(a), a):>14}"
            print(row)
        else:
            orig_attrs = compute_original_chained(fid)
            # Line 1: ORIGINAL
            row_o = f"  {fid:<30} {stage:<8} {path:<12}"
            for a in ATTR_NAMES:
                ov = orig_attrs.get(a) if orig_attrs else None
                row_o += f"  {'ORIG '+fmt(ov,a):>14}"
            # Line 2: CURRENT
            row_c = f"  {'':30} {'':8} {'':12}"
            for a in ATTR_NAMES:
                ov = orig_attrs.get(a) if orig_attrs else None
                cv = curr_attrs.get(a)
                diff = pct_diff(ov, cv) if ov else ''
                row_c += f"  {'NOW '+fmt(cv,a)+' '+diff:>14}"
            print(row_o)
            print(row_c)

    print()
