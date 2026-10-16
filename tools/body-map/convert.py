"""Turns Ryan Graves' anatomy SVGs (CC BY 4.0, via muscle_mapper) into Tonnage's body map asset.

Usage: python3 convert.py <folder with male_front_muscle_anatomy.svg and male_back_muscle_anatomy.svg> \
           app/src/main/assets/body_map.txt
The SVGs are in muscle_mapper's assets (advanced style); see CREDITS.md.

Each output line: view|kind|muscle|part|rule|stroke|d
  kind: B body (light skin), N neutral (hands, feet, face), M muscle, L detail line
"""
import re, sys, xml.etree.ElementTree as ET
NS = '{http://www.w3.org/2000/svg}'
src, out = sys.argv[1], sys.argv[2]

# id prefix -> (muscle group, part name). Checked in order, first match wins.
MAP = [
    ('anterior_deltoid', 'shoulders', 'Front delts'),
    ('lateral_deltoid', 'shoulders', 'Side delts'),
    ('posterior_deltoid', 'shoulders', 'Rear delts'),
    ('trapezius_upper', 'traps', 'Upper traps'),
    ('trapezius_middle', 'traps', 'Middle traps'),
    ('trapezius_lower', 'traps', 'Lower traps'),
    ('pectoralis_major', 'chest', 'Pecs'),
    ('biceps_brachii_caput_longum', 'biceps', 'Biceps long head'),
    ('biceps_brachii_caput_breve', 'biceps', 'Biceps short head'),
    ('brachialis', 'biceps', 'Brachialis'),
    ('triceps_brachii_caput_longum', 'triceps', 'Triceps long head'),
    ('triceps_brachii_caput_laterale', 'triceps', 'Triceps lateral head'),
    ('triceps_brachii_caput_mediale', 'triceps', 'Triceps medial head'),
    ('anconeus', 'triceps', 'Anconeus'),
    ('brachioradialis', 'forearms', 'Brachioradialis'),
    ('flexor_digitorum_superficialis', 'forearms', 'Finger flexors'),
    ('palmaris_longus', 'forearms', 'Wrist flexors'),
    ('flexor_carpi_radialis', 'forearms', 'Wrist flexors'),
    ('flexor_carpi_ulnaris', 'forearms', 'Wrist flexors'),
    ('pronator_teres', 'forearms', 'Pronator'),
    ('extensor_carpi_radialis_longus', 'forearms', 'Wrist extensors'),
    ('extensor_carpi_ulnaris', 'forearms', 'Wrist extensors'),
    ('extensor_digitorum_longus', 'calves', 'Toe extensors'),
    ('extensor_digitorum', 'forearms', 'Finger extensors'),
    ('rectus_abdominis', 'abdominals', 'Six-pack'),
    ('external_oblique', 'abdominals', 'Obliques'),
    ('latissimus_dorsi', 'lats', 'Lats'),
    ('infraspinatus', 'middle back', 'Infraspinatus'),
    ('teres', 'middle back', 'Teres major'),
    ('erector_spinae', 'lower back', 'Spinal erectors'),
    ('sternocleidomastoid', 'neck', 'Side of neck'),
    ('platysma', 'neck', 'Front of neck'),
    ('sternohyoid', 'neck', 'Front of neck'),
    ('rectus_femoris', 'quadriceps', 'Rectus femoris'),
    ('vastus_lateralis', 'quadriceps', 'Outer quad'),
    ('vastus_medialis', 'quadriceps', 'Inner quad (teardrop)'),
    ('sartoris', 'quadriceps', 'Sartorius'),
    ('adductor_longus', 'adductors', 'Adductor longus'),
    ('adductor_magnus', 'adductors', 'Adductor magnus'),
    ('pectineus', 'adductors', 'Pectineus'),
    ('gracilis', 'adductors', 'Gracilis'),
    ('gluteus_medius', 'abductors', 'Gluteus medius'),
    ('iliotibial_tract', 'abductors', 'IT band'),
    ('gluteus_maximus', 'glutes', 'Gluteus maximus'),
    ('semitendinosus', 'hamstrings', 'Inner hamstrings'),
    ('semimembranosus', 'hamstrings', 'Inner hamstrings'),
    ('biceps_femoris', 'hamstrings', 'Outer hamstring'),
    ('gastrocnemius', 'calves', 'Gastrocnemius'),
    ('soleus', 'calves', 'Soleus'),
    ('tibialis_anterior', 'calves', 'Shin (tibialis)'),
    ('extensor_hallucis_longus', 'calves', 'Toe extensors'),
    ('fibularis_longus', 'calves', 'Fibularis'),
]

# Shapes the artist left unnamed, by (view, group, position in group).
UNNAMED = {
    ('front', 'legs', 0): 'N', ('front', 'legs', 1): 'N',  # ankle highlights
    ('front', 'legs', 2): 'soleus',
    ('front', 'arms', 0): 'brachialis', ('front', 'arms', 1): 'brachialis',
    ('back', 'legs', 0): 'N', ('back', 'legs', 1): 'N', ('back', 'legs', 2): 'N', ('back', 'legs', 3): 'N',
    ('back', 'legs', 4): 'soleus', ('back', 'legs', 5): 'soleus',
    ('back', 'shoulders', 0): 'teres', ('back', 'shoulders', 1): 'teres',
}

# The artwork leaves the lower back as bare fascia; this adds the spinal erectors there, drawn under the
# lats, obliques and glutes so only the visible diamond shows. Left half, mirrored about x = 297.5.
LOWER_BACK_L = ('M297.5 332 C291 352 284 372 279 398 C274 420 266 436 255 446 C240 449 226 451 222 456 '
                'C236 466 252 480 266 500 C278 516 290 528 297.5 530 Z')

def num(m):
    v = round(float(m.group(0)), 1)
    return ('%g' % v)

def tidy(d):
    d = re.sub(r'-?\d*\.?\d+(?:e-?\d+)?', num, d)
    return re.sub(r'\s*([A-Za-z])\s*', r'\1', d).strip()

def mirror(d, axis):
    # Absolute M/L/C/Z only.
    toks = re.findall(r'[A-Za-z]|-?\d*\.?\d+', d)
    outp, i, cmd, idx = [], 0, None, 0
    for t in toks:
        if t.isalpha():
            cmd, idx = t, 0; outp.append(t); continue
        v = float(t)
        if cmd in 'MLC' and idx % 2 == 0: v = 2 * axis - v
        idx += 1
        outp.append('%g' % round(v, 1))
    s = ''
    for t in outp:
        s += t if t.isalpha() else ((' ' if s and not s[-1].isalpha() else '') + t)
    return s

lines = []
for view, fname in (('front', 'male_front_muscle_anatomy.svg'), ('back', 'male_back_muscle_anatomy.svg')):
    root = ET.parse(f'{src}/{fname}').getroot()
    counters = {}
    def emit(kind, muscle, part, rule, stroke, d):
        lines.append('|'.join([view, kind, muscle or '', part or '', rule, stroke, tidy(d)]))
    def walk(el, group):
        for c in el:
            tag = c.tag.replace(NS, '')
            if tag == 'g':
                walk(c, c.get('id') or group); continue
            if tag != 'path': continue
            pid, fill, stroke = c.get('id'), (c.get('fill') or '').strip(), c.get('stroke')
            rule = 'E' if c.get('fill-rule') == 'evenodd' else 'N'
            d = c.get('d')
            if fill in ('#BDBDBD', '#E0E0E0'):
                name = pid
                if pid is None:
                    n = counters.get(group, 0); counters[group] = n + 1
                    name = UNNAMED.get((view, group, n))
                    if name is None and group not in ('face', 'head'):
                        raise SystemExit(f'unknown unnamed shape {view} {group} {n}')
                if pid == 'underlayer':
                    emit('B', None, None, rule, '', d)
                    if view == 'back':
                        emit('M', 'lower back', 'Spinal erectors', 'N', '', LOWER_BACK_L)
                        emit('M', 'lower back', 'Spinal erectors', 'N', '', mirror(LOWER_BACK_L, 297.5))
                    continue
                hit = next(((m, p) for k, m, p in MAP if name and name.startswith(k)), None)
                if hit and fill == '#BDBDBD':
                    emit('M', hit[0], hit[1], rule, '', d)
                else:
                    emit('B' if fill == '#E0E0E0' else 'N', None, None, rule, '', d)
            if stroke == 'black':
                emit('L', None, None, 'N', c.get('stroke-width') or '1', d)
    walk(root, 'root')
    if view == 'back':
        # A soft centre line down the erectors.
        lines.append('back|L||||0.6|M297.5 340C297.5 400 297.5 470 297.5 522')
header = ('# Tonnage body map. Anatomy artwork by Ryan Graves (https://www.figma.com/community/file/1320468164820924031),\n'
          '# licensed CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/), taken from the muscle_mapper package\n'
          '# (https://github.com/suryamolly/muscle_mapper, advanced style). Adapted for Tonnage: recoloured, each muscle\n'
          '# tagged with a muscle group, coordinates rounded, lower-back erectors added. See CREDITS.md.\n'
          '# Format: view|kind|muscle|part|fill rule|stroke width|path. Front viewBox 587x1137, back 596x1133.\n')
open(out, 'w').write(header + '\n'.join(lines) + '\n')
import collections
print(len(lines), 'shapes', sum(len(l) for l in lines)//1024, 'KB')
c = collections.Counter((l.split('|')[0], l.split('|')[2]) for l in lines if l.split('|')[1] == 'M')
for k in sorted(c): print(k, c[k])
