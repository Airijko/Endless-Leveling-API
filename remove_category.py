import os, re

root = 'src/main/resources/classes'
updated = []

for subdir in sorted(os.listdir(root)):
    subpath = os.path.join(root, subdir)
    if not os.path.isdir(subpath):
        continue
    
    for fname in sorted(os.listdir(subpath)):
        if not fname.endswith('.yml'):
            continue
        fpath = os.path.join(subpath, fname)
        text = open(fpath, encoding='utf-8').read()
        
        # Remove the category line
        new_text = re.sub(r'^category:\s*"[^"]*"\s*\n', '', text, flags=re.MULTILINE)
        
        if new_text != text:
            open(fpath, 'w', encoding='utf-8', newline='\n').write(new_text)
            updated.append(fname)

print(f'Removed category from {len(updated)} files')
for f in updated[:5]:
    print(f'  {f}')
if len(updated) > 5:
    print(f'  ... and {len(updated)-5} more')
