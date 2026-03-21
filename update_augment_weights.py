#!/usr/bin/env python3
"""
Update augment YAML files with damage-type-based weights.
Sorcery augments: high weight when sorcery > strength
Strength augments: high weight when strength > sorcery
"""

import os
import yaml
from pathlib import Path

# Augment categorization
SORCERY_AUGMENTS = {
    "arcane_cataclysm", "arcane_comet", "arcane_instability", "arcane_mastery",
    "magic_blade", "magic_missle", "mana_infusion"
}

STRENGTH_AUGMENTS = {
    "bloodthirster", "blood_echo", "blood_frenzy", "blood_surge",
    "conqueror", "cripple", "drain", "executioner", "first_strike",
    "fleet_footwork", "giant_slayer", "overdrive", "overheal",
    "phantom_hits", "phase_rush", "predator", "reckoning",
    "snipers_reach", "soul_reaver", "time_master", "vampiric_strike",
    "vampirism", "wither"
}

AUGMENTS_DIR = Path("src/main/resources/augments")

def update_augment_weights(file_path, augment_name):
    """Update weights in an augment YAML file based on damage type."""
    
    with open(file_path, 'r') as f:
        data = yaml.safe_load(f)
    
    if data is None:
        print(f"❌ {augment_name}.yml is empty")
        return False
    
    # Initialize weights if not present
    if 'weights' not in data:
        data['weights'] = {}
    
    # Determine weight configuration based on augment type
    if augment_name in SORCERY_AUGMENTS:
        # Sorcery augments: high weight when sorcery > strength
        data['weights'] = {
            'default': 8,
            'conditions': [
                {
                    'when': {'sorcery_higher_than_strength': True},
                    'weight': 60
                },
                {
                    'when': {'strength_higher_than_sorcery': True},
                    'weight': 10
                }
            ]
        }
        category = "Sorcery"
    elif augment_name in STRENGTH_AUGMENTS:
        # Strength augments: high weight when strength > sorcery
        data['weights'] = {
            'default': 8,
            'conditions': [
                {
                    'when': {'strength_higher_than_sorcery': True},
                    'weight': 60
                },
                {
                    'when': {'sorcery_higher_than_strength': True},
                    'weight': 10
                }
            ]
        }
        category = "Strength"
    else:
        # Utility/Passive augments: neutral weights
        data['weights'] = {
            'default': 10,
            'conditions': []
        }
        category = "Utility"
    
    # Write back to file
    with open(file_path, 'w') as f:
        yaml.dump(data, f, default_flow_style=False, sort_keys=False)
    
    print(f"✅ {augment_name}.yml ({category})")
    return True

def main():
    """Process all augment YAML files."""
    
    if not AUGMENTS_DIR.exists():
        print(f"❌ Augments directory not found: {AUGMENTS_DIR}")
        return
    
    yml_files = sorted(AUGMENTS_DIR.glob("*.yml"))
    
    print(f"Processing {len(yml_files)} augment files...\n")
    
    updated = 0
    errors = 0
    
    for file_path in yml_files:
        augment_name = file_path.stem
        
        # Skip common.yml for now (will handle specially)
        if augment_name == "common":
            print(f"⏭️  {augment_name}.yml (will update separately)")
            continue
        
        try:
            if update_augment_weights(file_path, augment_name):
                updated += 1
            else:
                errors += 1
        except Exception as e:
            print(f"❌ Error processing {augment_name}.yml: {e}")
            errors += 1
    
    print(f"\n{'='*50}")
    print(f"Updated: {updated}")
    print(f"Errors: {errors}")
    print(f"{'='*50}")

if __name__ == "__main__":
    main()
