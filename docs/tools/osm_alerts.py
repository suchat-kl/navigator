"""ดึงจุดเตือน (ด่านชั่งน้ำหนัก ด่านเก็บเงิน กล้องจับความเร็ว) ในประเทศไทยจาก OpenStreetMap

    python docs/tools/osm_alerts.py

เขียน app/src/main/assets/alerts.geojson ที่แอปใช้เป็นข้อมูลตั้งต้น
ข้อมูล © ผู้ร่วมพัฒนา OpenStreetMap สัญญาอนุญาต ODbL (ต้องแสดงที่มาในแอป — มีในหน้าเกี่ยวกับแอปแล้ว)

รูปแบบแต่ละจุด (ใช้รูปแบบเดียวกันกับข้อมูลของหน่วยงานในอนาคต):
    {"type": "Feature",
     "geometry": {"type": "Point", "coordinates": [lon, lat]},
     "properties": {"kind": "weighbridge|toll_booth|speed_camera", "name": "...", "source": "osm/node/123"}}
รัศมี/ระยะเตือนกำหนดในแอปตามชนิด (AlertKind) ใส่ "radius" ใน properties เพื่อกำหนดเองรายจุดได้
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'alerts.geojson'))
ENDPOINTS = ['https://overpass-api.de/api/interpreter', 'https://overpass.private.coffee/api/interpreter']

# ชนิดจุด → ตัวกรอง OSM
KINDS = {
    'weighbridge': '["amenity"="weighbridge"]',
    'toll_booth': '["barrier"="toll_booth"]',
    'speed_camera': '["highway"="speed_camera"]',
}


def query():
    parts = ''.join(f'nwr{f}(area.th);' for f in KINDS.values())
    return ('[out:json][timeout:180];area["ISO3166-1"="TH"][admin_level=2]->.th;'
            f'({parts});out center tags;')


def fetch():
    data = urllib.parse.urlencode({'data': query()}).encode()
    last = None
    for attempt in range(4):
        for url in ENDPOINTS:
            try:
                req = urllib.request.Request(url, data=data, headers={'User-Agent': 'thai-navigator-osm-alerts/1.0'})
                with urllib.request.urlopen(req, timeout=240) as r:
                    body = r.read().decode('utf-8')
                return json.loads(body)
            except Exception as e:  # เซิร์ฟเวอร์ Overpass สาธารณะไม่ว่างบ่อย ลองใหม่/สลับเครื่อง
                last = e
                print('ลองใหม่:', url, e, file=sys.stderr)
        time.sleep(20 * (attempt + 1))
    raise SystemExit(f'ดึงข้อมูลจาก Overpass ไม่สำเร็จ: {last}')


def kind_of(tags):
    if tags.get('amenity') == 'weighbridge':
        return 'weighbridge'
    if tags.get('barrier') == 'toll_booth':
        return 'toll_booth'
    if tags.get('highway') == 'speed_camera':
        return 'speed_camera'
    return None


def main():
    d = fetch()
    features = []
    for e in d.get('elements', []):
        tags = e.get('tags', {})
        kind = kind_of(tags)
        if kind is None:
            continue
        if e['type'] == 'node':
            lon, lat = e['lon'], e['lat']
        elif 'center' in e:
            lon, lat = e['center']['lon'], e['center']['lat']
        else:
            continue
        props = {'kind': kind, 'source': f"osm/{e['type']}/{e['id']}"}
        name = tags.get('name:th') or tags.get('name')
        if name:
            props['name'] = name
        if kind == 'speed_camera' and tags.get('maxspeed', '').isdigit():
            props['maxspeed'] = int(tags['maxspeed'])
        features.append({'type': 'Feature', 'properties': props,
                         'geometry': {'type': 'Point', 'coordinates': [round(lon, 6), round(lat, 6)]}})
    features.sort(key=lambda f: f['properties']['source'])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    meta = {'type': 'FeatureCollection',
            'attribution': '© OpenStreetMap contributors, ODbL',
            'generated': d.get('osm3s', {}).get('timestamp_osm_base', ''),
            'features': features}
    with open(OUT, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(meta, f, ensure_ascii=False, separators=(',', ':'))
    counts = {}
    for f in features:
        counts[f['properties']['kind']] = counts.get(f['properties']['kind'], 0) + 1
    print('ok', OUT, counts)


if __name__ == '__main__':
    main()
