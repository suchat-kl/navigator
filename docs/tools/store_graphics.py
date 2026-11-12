"""สร้างภาพหน้าร้าน Google Play จากภาพหน้าจอดิบ

    python docs/tools/store_graphics.py

อ่าน  docs/play/graphics/raw/*.png  (ภาพจาก emulator 1080x2400 ถ่ายด้วยรุ่น debug โหมด screenshots ไม่มีโฆษณา)
      adb shell am start -n th.banceum.suchat.navigator/.MainActivity --ez screenshots true
เขียน docs/play/graphics/
      icon-512.png            ไอคอนแอป 512x512 (วาดตาม ic_launcher_foreground.xml)
      feature-1024x500.png    ภาพหน้าปก (Feature graphic)
      phone-01..07.png        ภาพหน้าจอโทรศัพท์ 1080x1920 พร้อมหัวข้อ
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, '..', 'play', 'graphics'))
RAW = os.path.join(OUT, 'raw')
FONT = os.path.join(HERE, 'fonts')

GREEN = (15, 123, 95)       # #0F7B5F สีพื้นไอคอน/สีหลักของแอป
DARK = (6, 64, 50)
YELLOW = (255, 200, 61)     # #FFC83D หมุดจุดหมาย
PALE = (207, 239, 228)      # #CFEFE4 เส้นทาง
SUB = (205, 238, 226)       # สีข้อความรองบนพื้นเขียว


def font(name, size):
    return ImageFont.truetype(os.path.join(FONT, name), size)


def gradient(w, h, top=GREEN, bottom=DARK):
    g = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / max(h - 1, 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return g


def cubic(p0, p1, p2, p3, n=24):
    """จุดบนเส้นโค้ง Bezier กำลังสาม (ตามคำสั่ง C ใน vector drawable)"""
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return pts


def draw_mark(size, crop=72):
    """วาดสัญลักษณ์ตาม vector 108x108 ของไอคอนแอป ตัดเอาเฉพาะกลาง crop หน่วย (ขยายให้เต็มขึ้น)"""
    ss = 4
    s = size * ss
    im = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    off = (108 - crop) / 2
    k = s / crop

    def P(pts):
        return [((x - off) * k, (y - off) * k) for x, y in pts]

    # เส้นทางคดเคี้ยว (stroke 5 ปลายมน)
    road = cubic((38, 66), (40, 56), (52, 60), (54, 52)) + cubic((54, 52), (56, 44), (62, 46), (66, 44))[1:]
    w = 5 * k
    d.line(P(road), fill=PALE, width=round(w), joint='curve')
    for x, y in P([road[0], road[-1]]):
        d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=PALE)
    # หมุดจุดหมาย
    pin = (cubic((68, 24), (63, 24), (59, 28), (59, 33)) + cubic((59, 33), (59, 40), (68, 47), (68, 47))[1:]
           + cubic((68, 47), (68, 47), (77, 40), (77, 33))[1:] + cubic((77, 33), (77, 28), (73, 24), (68, 24))[1:])
    d.polygon(P(pin), fill=YELLOW)
    (cx, cy), = P([(68, 33)])
    r = 3.5 * k
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GREEN)
    # ลูกศรนำทาง
    d.polygon(P([(38, 60), (47, 79), (38, 74), (29, 79)]), fill=(255, 255, 255))
    return im.resize((size, size), Image.LANCZOS)


def icon(size=512):
    im = Image.new('RGBA', (size, size), GREEN + (255,))
    im.alpha_composite(draw_mark(size))
    return im


def rounded(im, r):
    m = Image.new('L', im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], r, fill=255)
    out = im.convert('RGBA')
    out.putalpha(m)
    return out


def shadow(canvas, box, r, blur=24, alpha=110):
    sh = Image.new('RGBA', canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(box, r, fill=(0, 0, 0, alpha))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(blur)))


def screen(name):
    """ภาพหน้าจอดิบ ตัดแถบสถานะ (นาฬิกา/แบตเตอรี่) และแถบนำทางด้านล่างออก"""
    im = Image.open(os.path.join(RAW, name)).convert('RGB')
    return im.crop((0, 66, im.width, im.height - 70))


def center_text(d, y, text, f, fill, w):
    tw = d.textlength(text, font=f)
    d.text(((w - tw) / 2, y), text, font=f, fill=fill)


def phone_shot(raw, title, sub, out):
    W, H = 1080, 1920
    c = gradient(W, H).convert('RGBA')
    d = ImageDraw.Draw(c)
    size = 68
    while d.textlength(title, font=font('Kanit-Bold.ttf', size)) > W - 140:   # หัวข้อยาวให้ย่อจนพอดีขอบ
        size -= 2
    center_text(d, 90 + (68 - size) // 2, title, font('Kanit-Bold.ttf', size), (255, 255, 255), W)
    center_text(d, 205, sub, font('Sarabun-Regular.ttf', 40), SUB, W)
    # ย่อให้เห็นทั้งจอ (แผงล่างของแอปมีข้อมูลสำคัญ เช่น เวลาเดินทางและปุ่มเริ่มนำทาง)
    s = screen(raw)
    y, bez, bottom = 330, 20, 60
    sh = H - y - bez - bottom
    sw = round(s.width * sh / s.height)
    s = s.resize((sw, sh), Image.LANCZOS)
    x = (W - sw) // 2
    # กรอบโทรศัพท์แบบเรียบ: ขอบดำมน
    box = (x - bez, y - bez, x + sw + bez, y + sh + bez)
    shadow(c, box, 64)
    ImageDraw.Draw(c).rounded_rectangle(box, 64, fill=(20, 24, 31, 255))
    c.alpha_composite(rounded(s, 46), (x, y))
    c.convert('RGB').save(os.path.join(OUT, out), optimize=True)


def feature():
    W, H = 1024, 500
    c = gradient(W, H, (20, 140, 108), DARK).convert('RGBA')
    d = ImageDraw.Draw(c)
    # วงกลมจาง ๆ ตกแต่งพื้นหลัง
    deco = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(deco)
    dd.ellipse((560, -160, 1180, 460), fill=(255, 255, 255, 18))
    dd.ellipse((-140, 330, 260, 730), fill=(255, 255, 255, 12))
    c.alpha_composite(deco)
    ic = rounded(icon(150), 34)
    shadow(c, (64, 64, 214, 214), 34, blur=14, alpha=90)
    c.alpha_composite(ic, (64, 64))
    d.text((64, 232), 'นำทางไทย OSM', font=font('Kanit-Bold.ttf', 60), fill=(255, 255, 255))
    f = font('Sarabun-Regular.ttf', 28)
    for i, line in enumerate(('ค้นหาสถานที่  •  หาเส้นทาง  •  นำทางด้วยเสียง',
                              'แผนที่ OpenStreetMap ป้ายชื่อภาษาไทย',
                              'ไม่ต้องสมัครสมาชิก ใช้งานได้ทันที')):
        d.text((66, 318 + i * 42), line, font=f, fill=SUB)
    # โทรศัพท์ทางขวา ล้นขอบล่าง
    sw = 300
    s = screen('05_navigate.png')
    s = s.resize((sw, round(s.height * sw / s.width)), Image.LANCZOS)
    x, y, bez = 690, 52, 10
    shadow(c, (x - bez, y - bez, x + sw + bez, H + 100), 34, blur=16)
    ImageDraw.Draw(c).rounded_rectangle((x - bez, y - bez, x + sw + bez, H + 100), 34, fill=(20, 24, 31, 255))
    c.alpha_composite(rounded(s, 24), (x, y))
    c.convert('RGB').save(os.path.join(OUT, 'feature-1024x500.png'), optimize=True)


SHOTS = [
    ('05_navigate.png', 'นำทางด้วยเสียงภาษาไทย', 'บอกระยะและทิศทางก่อนถึงทุกจุดเลี้ยว'),
    ('07_speed.png', 'เตือนเมื่อขับเร็วเกินกำหนด', 'แสดงป้ายจำกัดความเร็วของถนน  พร้อมเสียงเตือน'),
    ('03_route.png', 'หาเส้นทางในไม่กี่วินาที', 'รถยนต์ จักรยาน หรือเดิน  พร้อมเวลาโดยประมาณ'),
    ('02_search.png', 'ค้นหาสถานที่ทั่วไทย', 'ผลที่ใกล้คุณขึ้นก่อน  หรือแตะค้างบนแผนที่'),
    ('04_steps.png', 'ขั้นตอนการเลี้ยวครบทุกจุด', 'ดูเส้นทางทั้งหมดก่อนออกเดินทาง'),
    ('01_map.png', 'แผนที่ OpenStreetMap', 'ป้ายชื่อถนนและสถานที่เป็นภาษาไทย'),
    ('06_saved.png', 'บันทึกสถานที่ไปบ่อย', 'เก็บในเครื่องของคุณ  ไม่ต้องมีบัญชี'),
]

if __name__ == '__main__':
    icon().convert('RGB').save(os.path.join(OUT, 'icon-512.png'))   # Play รับ PNG 512x512 (ไม่ต้องมีพื้นโปร่งใส)
    feature()
    for i, (raw, t, s) in enumerate(SHOTS, 1):
        phone_shot(raw, t, s, 'phone-%02d.png' % i)
    print('ok ->', OUT)
