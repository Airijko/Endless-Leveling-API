import os, re

root = 'src/main/resources/classes'

# Map class base name to metadata
CLASS_META = {
    'adventurer': {
        'roles': ['Skirmisher'],
        'damage_type': 'Hybrid',
        'range_type': 'melee/range',
    },
    'arcanist': {
        'roles': ['Mage'],
        'damage_type': 'Magic',
        'range_type': 'range',
    },
    'assassin': {
        'roles': ['Assassin', 'Diver'],
        'damage_type': 'Physical',
        'range_type': 'melee',
    },
    'battlemage': {
        'roles': ['BattleMage'],
        'damage_type': 'Magic',
        'range_type': 'melee/range',
    },
    'brawler': {
        'roles': ['Skirmisher', 'Juggernaut'],
        'damage_type': 'Physical',
        'range_type': 'melee',
    },
    'duelist': {
        'roles': ['Skirmisher'],
        'damage_type': 'Physical',
        'range_type': 'melee',
    },
    'healer': {
        'roles': ['Support'],
        'damage_type': 'Magic',
        'range_type': 'range',
    },
    'juggernaut': {
        'roles': ['Juggernaut'],
        'damage_type': 'Physical',
        'range_type': 'melee',
    },
    'mage': {
        'roles': ['Mage'],
        'damage_type': 'Magic',
        'range_type': 'range',
    },
    'marksman': {
        'roles': ['Marksman'],
        'damage_type': 'Physical',
        'range_type': 'range',
    },
    'necromancer': {
        'roles': ['Battlemage'],
        'damage_type': 'Magic',
        'range_type': 'melee/range',
    },
    'oracle': {
        'roles': ['Support'],
        'damage_type': 'Magic',
        'range_type': 'range',
    },
    'slayer': {
        'roles': ['Skirmisher'],
        'damage_type': 'Physical',
        'range_type': 'melee',
    },
    'vanguard': {
        'roles': ['Vanguard'],
        'damage_type': 'Hybrid',
        'range_type': 'melee',
    },
}

updated = []
failed = []

# Walk through subdirectories
for subdir in sorted(os.listdir(root)):
    subpath = os.path.join(root, subdir)
    if not os.path.isdir(subpath):
        continue
    
    # Find class base name from folder
    class_base = None
    for base in CLASS_META:
        if subdir == base or subdir.startswith(base + '_'):
            class_base = base
            break
    
    if not class_base:
        continue
    
    # Process each file in the subdir
    for fname in sorted(os.listdir(subpath)):
        if not fname.endswith('.yml'):
            continue
        fpath = os.path.join(subpath, fname)
        text = open(fpath, encoding='utf-8').read()
        lines = text.splitlines(keepends=True)
        
        meta = CLASS_META[class_base]
        roles_yaml = '\n'.join([f'  - {r}' for r in meta['roles']])
        
        # Replace the role line(s)
        new_lines = []
        i = 0
        found_role = False
        while i < len(lines):
            line = lines[i]
            # Look for "role: ..." line
            if re.match(r'^role:\s*', line):
                found_role = True
                # Replace this line with roles block and new fields
                new_lines.append(f'roles:\n{roles_yaml}\n')
                new_lines.append(f'damage_type: {meta["damage_type"]}\n')
                new_lines.append(f'range_type: {meta["range_type"]}\n')
                i += 1
            else:
                new_lines.append(line)
                i += 1
        
        if not found_role:
            failed.append((fname, 'no_role_field'))
            continue
        
        new_text = ''.join(new_lines)
        open(fpath, 'w', encoding='utf-8', newline='\n').write(new_text)
        updated.append(fname)

print(f'Updated: {len(updated)}')
for f in updated[:10]:
    print(f'  {f}')
if len(updated) > 10:
    print(f'  ... and {len(updated)-10} more')

if failed:
    print(f'\nFailed: {len(failed)}')
    for f, reason in failed:
        print(f'  {f}: {reason}')
