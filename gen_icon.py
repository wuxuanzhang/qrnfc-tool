import struct, zlib, os

W = H = 108

def make_png(data, path):
    def chunk(tag, payload):
        c = tag + payload
        return struct.pack('>I', len(payload)) + c + struct.pack('>I', zlib.crc32(c))
    raw = b''
    for y in range(H):
        raw += b'\x00' + b''.join(bytes(p) + b'\xff' for p in data[y])
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw))
    png += chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(png)

BG = (0x1A, 0x73, 0xE8)
WHITE = (255, 255, 255)

data = [[BG for _ in range(W)] for _ in range(H)]

def fill_rect(x0, y0, x1, y1):
    for y in range(max(0, y0), min(H, y1)):
        for x in range(max(0, x0), min(W, x1)):
            data[y][x] = WHITE

u = 9
# finder corners: 7x7 units white ring with blue center and white core
def finder(cx, cy):
    fill_rect(cx, cy, cx + 7 * u, cy + 7 * u)                 # outer
    fill_rect(cx + u, cy + u, cx + 6 * u, cy + 6 * u)         # inner ring -> leave? 
    # center white core on blue ring:
    fill_rect(cx + u, cy + u, cx + 6 * u, cy + 6 * u)         # blue via background? no.

# Rebuild finder correctly: outer white, blue ring, white core
def finder2(cx, cy):
    fill_rect(cx, cy, cx + 7 * u, cy + 7 * u)                 # outer white
    # blue ring: restore background over ring
    for y in range(cy + u, cy + 6 * u):
        for x in range(cx + u, cx + 6 * u):
            data[y][x] = BG
    fill_rect(cx + 2 * u, cy + 2 * u, cx + 5 * u, cy + 5 * u) # white core

finder2(8, 8)
finder2(W - 8 - 7 * u, 8)
finder2(8, H - 8 - 7 * u)

# data blocks
for (x, y) in [(40,40),(40,58),(40,76),(58,40),(76,40),(58,76),(76,58),(58,58),(40,94),(94,40)]:
    fill_rect(x, y, x + 10, y + 10)

out = '/home/user/Doubao/chats/38445444486625538/ScanNfcTool/app/src/main/res/mipmap'
os.makedirs(out, exist_ok=True)
make_png(data, os.path.join(out, 'ic_launcher.png'))
make_png(data, os.path.join(out, 'ic_launcher_round.png'))
print("block icons regenerated:", os.path.getsize(os.path.join(out, 'ic_launcher.png')), "bytes")
