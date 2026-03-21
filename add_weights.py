import os, re

root = 'src/main/resources/augments'

WEIGHTS = {
    # === MAGIC_ON_HIT / MAGIC_STAT ===
    'arcane_cataclysm': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 200
      weight: 80
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'arcane_comet': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 150
      weight: 70
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'magic_missle': """weights:
  default: 8
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 60
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 2""",
    'arcane_instability': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 70
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'arcane_mastery': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 70
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'mana_infusion': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 65
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'magic_blade': """weights:
  default: 5
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 70
    - when:
        max_skill_levels:
          sorcery: 50
      weight: 1""",
    'titans_wisdom': """weights:
  default: 10
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 60
    - when:
        min_skill_levels:
          life_force: 300
      weight: 40""",

    # === STRENGTH ===
    'brute_force': """weights:
  default: 8
  conditions:
    - when:
        min_skill_levels:
          strength: 200
      weight: 60
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 2""",
    'titans_might': """weights:
  default: 10
  conditions:
    - when:
        min_skill_levels:
          strength: 150
      weight: 60
    - when:
        min_skill_levels:
          life_force: 300
      weight: 40""",
    'giant_slayer': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          strength: 150
      weight: 60""",
    'phantom_hits': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 55""",

    # === FEROCITY / CRIT ===
    'executioner': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 60""",
    'first_strike': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 55""",
    'cutdown': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 50""",
    'absolute_focus': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 150
      weight: 65
    - when:
        max_skill_levels:
          ferocity: 30
      weight: 2""",
    'overdrive': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 50
    - when:
        min_skill_levels:
          haste: 100
      weight: 50""",
    'death_bomb': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 40""",
    'vampiric_strike': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 55""",
    'overheal': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 50
    - when:
        min_skill_levels:
          precision: 100
      weight: 50""",
    'critical_guard': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          ferocity: 100
      weight: 55""",
    'snipers_reach': """weights:
  default: 8
  conditions:
    - when:
        min_skill_levels:
          precision: 100
      weight: 65
    - when:
        min_skill_levels:
          haste: 100
      weight: 45""",

    # === HASTE / MOBILITY ===
    'fleet_footwork': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          haste: 100
      weight: 55""",
    'phase_rush': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          haste: 100
      weight: 60""",
    'supersonic': """weights:
  default: 10
  conditions:
    - when:
        min_skill_levels:
          haste: 150
      weight: 60
    - when:
        min_skill_levels:
          strength: 150
      weight: 2""",
    'predator': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          haste: 100
      weight: 60""",

    # === LIFESTEAL ===
    'vampirism': """weights:
  default: 20
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 40
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 40""",
    'blood_frenzy': """weights:
  default: 20
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 45
    - when:
        min_skill_levels:
          haste: 100
      weight: 45""",
    'blood_echo': """weights:
  default: 18
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 40""",
    'blood_surge': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 50
    - when:
        min_skill_levels:
          strength: 100
      weight: 40""",
    'bloodthirster': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          strength: 150
      weight: 55""",
    'drain': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 50
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 40""",

    # === ON_HIT / MIXED OFFENSE ===
    'conqueror': """weights:
  default: 18
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 45
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 45""",
    'cripple': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 50""",
    'wither': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 45
    - when:
        min_skill_levels:
          strength: 100
      weight: 35""",
    'reckoning': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          life_force: 300
      weight: 60""",

    # === ON_KILL ===
    'soul_reaver': """weights:
  default: 18
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 50
    - when:
        min_skill_levels:
          strength: 100
      weight: 40""",
    'time_master': """weights:
  default: 20""",

    # === ON_DAMAGE_TAKEN ===
    'burn': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          defense: 100
      weight: 50
    - when:
        min_skill_levels:
          life_force: 200
      weight: 40""",
    'frozen_domain': """weights:
  default: 12
  conditions:
    - when:
        min_skill_levels:
          defense: 100
      weight: 50
    - when:
        min_skill_levels:
          life_force: 200
      weight: 40""",
    'endure_pain': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          defense: 100
      weight: 55
    - when:
        min_skill_levels:
          life_force: 200
      weight: 45""",
    'protective_bubble': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          defense: 100
      weight: 55""",

    # === ON_LOW_HP ===
    'fortress': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          defense: 100
      weight: 60
    - when:
        min_skill_levels:
          life_force: 300
      weight: 50""",
    'rebirth': """weights:
  default: 18
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 55
    - when:
        min_skill_levels:
          defense: 100
      weight: 45""",
    'undying_rage': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 60
    - when:
        min_skill_levels:
          strength: 150
      weight: 50""",

    # === ON_DEATH ===
    'bailout': """weights:
  default: 20""",
    'nesting_doll': """weights:
  default: 12""",

    # === PASSIVE_STAT: universal/mixed ===
    'four_leaf_clover': """weights:
  default: 25""",
    'glass_cannon': """weights:
  default: 10
  conditions:
    - when:
        min_skill_levels:
          haste: 100
      weight: 50""",
    'goliath': """weights:
  default: 20
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 50""",
    'raging_momentum': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          strength: 100
      weight: 55
    - when:
        min_skill_levels:
          sorcery: 100
      weight: 55""",
    'raid_boss': """weights:
  default: 8
  conditions:
    - when:
        min_skill_levels:
          life_force: 400
      weight: 70
    - when:
        min_skill_levels:
          defense: 200
      weight: 60""",
    'tank_engine': """weights:
  default: 15
  conditions:
    - when:
        min_skill_levels:
          life_force: 200
      weight: 60
    - when:
        min_skill_levels:
          defense: 100
      weight: 50""",

    # === COMMON_STAT ===
    'common': """weights:
  default: 35""",
}

updated = []
skipped = []
already = []

for fname in sorted(os.listdir(root)):
    if not fname.endswith('.yml'):
        continue
    aug_id = fname[:-4]
    path = os.path.join(root, fname)
    text = open(path, encoding='utf-8').read()

    if 'weights:' in text:
        already.append(aug_id)
        continue

    if aug_id not in WEIGHTS:
        skipped.append(aug_id)
        continue

    block = WEIGHTS[aug_id]
    # Insert after the 'enabled:' line
    new_text = re.sub(
        r'(enabled:\s*(?:true|false)\s*\n)',
        r'\1\n' + block + '\n\n',
        text,
        count=1
    )
    if new_text == text:
        skipped.append(aug_id + ' (no enabled line found)')
        continue

    open(path, 'w', encoding='utf-8', newline='\n').write(new_text)
    updated.append(aug_id)

print(f'Updated: {len(updated)}')
for x in updated:
    print(' ', x)
if skipped:
    print(f'Skipped ({len(skipped)}):')
    for x in skipped:
        print(' ', x)
if already:
    print(f'Already had weights ({len(already)}):')
