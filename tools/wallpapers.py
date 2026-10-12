# Gera os papeis de parede P&B a partir da logonova.png.
# uso: python3 tools/wallpapers.py LARGURA ALTURA [logo.png]
# a logo e branca sobre fundo de cor saturada (o canal verde vira a mascara)
import sys
from PIL import Image, ImageFilter, ImageDraw, ImageChops

W, H = int(sys.argv[1]), int(sys.argv[2])
src = Image.open(sys.argv[3] if len(sys.argv) > 3 else 'tools/logo.png').convert('RGB')
# fundo roxo tem G~0 e a logo e branca: o canal verde ja e a mascara com antialias
mask = src.split()[1]
mask = mask.crop(mask.point(lambda v: 255 if v > 128 else 0).getbbox())

def logo_mask(width):
    h = round(mask.height * width / mask.width)
    m = mask.resize((width * 2, h * 2), Image.LANCZOS).filter(ImageFilter.GaussianBlur(2))
    # curva suave pra recuperar borda nitida depois do upscale
    m = m.point(lambda v: 0 if v < 90 else 255 if v > 165 else int((v - 90) / 75 * 255))
    return m.resize((width, h), Image.LANCZOS)

def radial(w, h, center_level, cy):
    g = Image.new('L', (w, h), 0)
    d = ImageDraw.Draw(g)
    r = int(w * 0.9)
    d.ellipse((w // 2 - r, int(h * cy) - r, w // 2 + r, int(h * cy) + r), fill=center_level)
    return g.filter(ImageFilter.GaussianBlur(w // 3))

def make(path, logo_w, logo_level, glow, cy):
    bg = radial(W, H, glow, cy)
    m = logo_mask(logo_w)
    x, y = (W - m.width) // 2, int(H * cy - m.height / 2)
    out = bg.copy()
    out.paste(Image.new('L', m.size, logo_level), (x, y), m)
    out.convert('RGB').save(path, optimize=True)

# home: logo discreta, a lista de apps passa por cima
make('assets/wall_home.png', int(W * 0.62), 34, 16, 0.58)
# bloqueio: logo clara, e o "rosto" do celular
make('assets/wall_lock.png', int(W * 0.50), 225, 28, 0.55)

# icone do app
ic = Image.new('L', (192, 192), 0)
m = logo_mask(128)
ic.paste(Image.new('L', m.size, 255), ((192 - m.width) // 2, (192 - m.height) // 2), m)
ic.convert('RGB').save('res/mipmap/ic_launcher.png')
print('ok', W, H)
