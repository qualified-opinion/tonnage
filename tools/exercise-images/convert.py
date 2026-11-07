"""Turns free-exercise-db's exercise photos (public domain) into Tonnage's picture assets.

Usage: python3 tools/exercise-images/convert.py <free-exercise-db/exercises folder> \
           app/src/main/assets/exercises.json app/src/main/assets/exercise_images
Each bundled exercise gets <id>/0.webp and <id>/1.webp (two positions of the movement),
scaled to at most 360 px wide at WebP quality 55 to keep the APK small (about 12 MB for all).
Exercises without photos upstream are listed at the end and get none (the app shows a placeholder).
Needs Pillow with WebP support.
"""
import json, os, sys
from PIL import Image

WIDTH, QUALITY = 360, 55
src, library, out = sys.argv[1], sys.argv[2], sys.argv[3]
missing = []
for exercise in json.load(open(library, encoding='utf-8')):
    eid = exercise['id']
    for frame in (0, 1):
        photo = os.path.join(src, eid, f'{frame}.jpg')
        if not os.path.exists(photo):
            missing.append(eid)
            continue
        image = Image.open(photo).convert('RGB')
        if image.width > WIDTH:
            image = image.resize((WIDTH, round(image.height * WIDTH / image.width)), Image.LANCZOS)
        os.makedirs(os.path.join(out, eid), exist_ok=True)
        image.save(os.path.join(out, eid, f'{frame}.webp'), 'WEBP', quality=QUALITY, method=6)
print('No photos for:', sorted(set(missing)))
