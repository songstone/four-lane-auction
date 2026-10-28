import { createHash } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { deflateSync } from 'node:zlib';

// Original geometric demo artwork. No external images, fonts, or brands.
// The four resulting PNG illustrations are dedicated under CC0-1.0.
const WIDTH = 640;
const HEIGHT = 480;
const SCALE = 2;
const palette = {
  ink: '#243F4A', dark: '#17313B', teal: '#3B8982', mint: '#A9D2C3',
  cream: '#F5F0DE', gold: '#E7AD58', orange: '#D97C4C', white: '#FFFFFF',
};

function rgb(hex) {
  return [1, 3, 5].map((offset) => Number.parseInt(hex.slice(offset, offset + 2), 16));
}

class Canvas {
  constructor(background) {
    this.width = WIDTH * SCALE;
    this.height = HEIGHT * SCALE;
    this.pixels = Buffer.alloc(this.width * this.height * 3);
    const color = rgb(background);
    for (let i = 0; i < this.pixels.length; i += 3) {
      for (let channel = 0; channel < 3; channel++) this.pixels[i + channel] = color[channel];
    }
  }

  shape(left, top, right, bottom, color, contains) {
    const channels = rgb(color);
    const x0 = Math.max(0, Math.floor(left * SCALE));
    const y0 = Math.max(0, Math.floor(top * SCALE));
    const x1 = Math.min(this.width, Math.ceil(right * SCALE));
    const y1 = Math.min(this.height, Math.ceil(bottom * SCALE));
    for (let y = y0; y < y1; y++) {
      for (let x = x0; x < x1; x++) {
        if (!contains((x + 0.5) / SCALE, (y + 0.5) / SCALE)) continue;
        const i = (y * this.width + x) * 3;
        for (let channel = 0; channel < 3; channel++) this.pixels[i + channel] = channels[channel];
      }
    }
  }

  rect(x, y, width, height, radius, color) {
    const r = Math.min(radius, width / 2, height / 2);
    this.shape(x, y, x + width, y + height, color, (px, py) => {
      const dx = Math.max(x + r - px, 0, px - (x + width - r));
      const dy = Math.max(y + r - py, 0, py - (y + height - r));
      return dx * dx + dy * dy <= r * r;
    });
  }

  ellipse(x, y, rx, ry, color) {
    this.shape(x - rx, y - ry, x + rx, y + ry, color,
      (px, py) => ((px - x) / rx) ** 2 + ((py - y) / ry) ** 2 <= 1);
  }

  line(x1, y1, x2, y2, width, color) {
    const r = width / 2;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const lengthSquared = dx * dx + dy * dy;
    this.shape(Math.min(x1, x2) - r, Math.min(y1, y2) - r,
      Math.max(x1, x2) + r, Math.max(y1, y2) + r, color, (x, y) => {
        const t = lengthSquared === 0 ? 0
          : Math.max(0, Math.min(1, ((x - x1) * dx + (y - y1) * dy) / lengthSquared));
        return (x - x1 - t * dx) ** 2 + (y - y1 - t * dy) ** 2 <= r * r;
      });
  }

  polygon(points, color) {
    const xs = points.map(([x]) => x);
    const ys = points.map(([, y]) => y);
    this.shape(Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys), color, (x, y) => {
      let inside = false;
      for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
        const [xi, yi] = points[i];
        const [xj, yj] = points[j];
        if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
      }
      return inside;
    });
  }

  // Handwritten 5x7 geometric glyph patterns, not an imported font.
  demoBadge() {
    const glyphs = [
      ['11110', '10001', '10001', '10001', '10001', '10001', '11110'],
      ['11111', '10000', '10000', '11110', '10000', '10000', '11111'],
      ['10001', '11011', '10101', '10101', '10001', '10001', '10001'],
      ['01110', '10001', '10001', '10001', '10001', '10001', '01110'],
    ];
    this.rect(34, 30, 108, 34, 17, palette.white);
    this.ellipse(50, 47, 4, 4, palette.teal);
    for (let letter = 0; letter < glyphs.length; letter++) {
      for (let row = 0; row < 7; row++) {
        for (let col = 0; col < 5; col++) {
          if (glyphs[letter][row][col] === '1') this.rect(63 + letter * 16 + col * 2, 40 + row * 2, 2, 2, 0, palette.ink);
        }
      }
    }
  }

  rawPngRows() {
    const stride = WIDTH * 3 + 1;
    const rows = Buffer.alloc(stride * HEIGHT); // PNG filter 0 at the start of every row.
    for (let y = 0; y < HEIGHT; y++) {
      for (let x = 0; x < WIDTH; x++) {
        for (let channel = 0; channel < 3; channel++) {
          let sum = 0;
          for (let sy = 0; sy < SCALE; sy++) {
            for (let sx = 0; sx < SCALE; sx++) {
              sum += this.pixels[((y * SCALE + sy) * this.width + x * SCALE + sx) * 3 + channel];
            }
          }
          rows[y * stride + 1 + x * 3 + channel] = Math.round(sum / (SCALE * SCALE));
        }
      }
    }
    return rows;
  }
}

function camera(canvas) {
  canvas.ellipse(323, 387, 188, 20, '#D6E3DD');
  canvas.rect(163, 164, 310, 192, 27, palette.dark);
  canvas.rect(163, 163, 310, 70, 24, palette.cream);
  canvas.rect(163, 207, 310, 24, 0, palette.cream);
  canvas.rect(175, 236, 60, 104, 14, palette.teal);
  canvas.rect(195, 145, 57, 19, 6, palette.gold);
  canvas.rect(278, 133, 91, 41, 10, palette.ink);
  canvas.rect(293, 143, 61, 22, 5, '#698A8B');
  canvas.rect(405, 185, 40, 23, 5, '#C7D3CA');
  canvas.ellipse(329, 267, 83, 83, palette.cream);
  canvas.ellipse(329, 267, 72, 72, palette.teal);
  canvas.ellipse(329, 267, 59, 59, palette.ink);
  canvas.ellipse(329, 267, 43, 43, '#446C7A');
  canvas.ellipse(329, 267, 27, 27, palette.dark);
  canvas.ellipse(313, 249, 12, 12, '#9CC8C1');
  canvas.ellipse(349, 288, 6, 6, '#719F9F');
  canvas.ellipse(451, 245, 5, 5, palette.orange);
}

function headphones(canvas) {
  canvas.ellipse(320, 398, 139, 17, '#DEDCD3');
  canvas.ellipse(320, 238, 132, 138, palette.ink);
  canvas.ellipse(320, 242, 107, 112, '#F4EEE1');
  canvas.rect(185, 248, 270, 148, 0, '#F4EEE1');
  canvas.ellipse(320, 221, 110, 109, palette.gold);
  canvas.ellipse(320, 228, 94, 101, '#F4EEE1');
  canvas.rect(218, 231, 204, 137, 0, '#F4EEE1');
  canvas.line(200, 226, 200, 301, 15, palette.ink);
  canvas.line(440, 226, 440, 301, 15, palette.ink);
  canvas.rect(183, 269, 52, 108, 23, palette.ink);
  canvas.rect(210, 277, 38, 96, 17, palette.teal);
  canvas.rect(405, 269, 52, 108, 23, palette.ink);
  canvas.rect(392, 277, 38, 96, 17, palette.teal);
  canvas.line(194, 291, 194, 351, 5, '#5D7680');
  canvas.line(446, 291, 446, 351, 5, '#5D7680');
  canvas.ellipse(320, 398, 139, 17, '#DEDCD3');
}

function keyboard(canvas) {
  canvas.ellipse(320, 360, 241, 22, '#D5E1DE');
  canvas.rect(80, 174, 480, 168, 22, palette.dark);
  canvas.rect(80, 164, 480, 161, 22, palette.ink);
  canvas.rect(96, 180, 448, 128, 12, '#48636B');
  for (let row = 0; row < 3; row++) {
    for (let col = 0; col < 12; col++) {
      const x = 107 + col * 35;
      const y = 190 + row * 30;
      const color = row === 0 && col === 0 ? palette.orange
        : col === 11 ? palette.mint : palette.cream;
      canvas.rect(x, y + 3, 29, 24, 5, '#273F48');
      canvas.rect(x, y, 29, 23, 5, color);
      if (!(row === 0 && col === 0)) canvas.line(x + 11, y + 10, x + 17, y + 10, 2, '#A7B0A4');
    }
  }
  const bottomKeys = [[107, 40], [154, 40], [201, 238], [446, 40], [493, 29]];
  for (const [x, width] of bottomKeys) {
    canvas.rect(x, 283, width, 21, 4, '#273F48');
    canvas.rect(x, 280, width, 21, 4, width === 238 ? palette.gold : palette.cream);
  }
  canvas.line(319, 162, 319, 132, 8, palette.ink);
  canvas.line(319, 132, 375, 132, 8, palette.ink);
  canvas.ellipse(516, 169, 3, 3, palette.gold);
}

function lamp(canvas) {
  canvas.polygon([[262, 245], [389, 245], [457, 385], [189, 385]], '#F9E6B3');
  canvas.ellipse(320, 401, 164, 16, '#DEDCCE');
  canvas.rect(260, 372, 156, 25, 12, palette.ink);
  canvas.rect(272, 366, 132, 14, 7, '#567B7C');
  canvas.line(341, 366, 408, 264, 17, palette.ink);
  canvas.line(408, 264, 337, 151, 17, palette.ink);
  canvas.line(352, 366, 420, 264, 5, '#739591');
  canvas.ellipse(408, 264, 20, 20, palette.ink);
  canvas.ellipse(408, 264, 10, 10, palette.gold);
  canvas.ellipse(337, 151, 17, 17, palette.ink);
  canvas.polygon([[289, 139], [345, 139], [390, 236], [244, 236]], palette.orange);
  canvas.ellipse(317, 236, 73, 15, '#A85635');
  canvas.ellipse(317, 238, 59, 9, palette.cream);
  canvas.line(299, 152, 277, 212, 8, '#EAA16D');
  canvas.ellipse(317, 245, 19, 10, palette.gold);
  canvas.ellipse(382, 374, 5, 3, palette.gold);
}

const drawings = {
  camera: { background: '#EBF2EC', draw: camera },
  headphones: { background: '#F4EEE1', draw: headphones },
  keyboard: { background: '#E9F0EC', draw: keyboard },
  lamp: { background: '#F3EEDC', draw: lamp },
};

const crcTable = Array.from({ length: 256 }, (_, index) => {
  let value = index;
  for (let bit = 0; bit < 8; bit++) value = value & 1 ? 0xEDB88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});

function crc32(bytes) {
  let crc = 0xFFFFFFFF;
  for (const value of bytes) crc = crcTable[(crc ^ value) & 0xFF] ^ (crc >>> 8);
  return (crc ^ 0xFFFFFFFF) >>> 0;
}

function chunk(type, data) {
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

export function createDemoImage(name) {
  const drawing = drawings[name];
  if (!drawing) throw new Error('Unknown demo image');
  const canvas = new Canvas(drawing.background);
  canvas.ellipse(535, 88, 39, 39, '#FFFFFF');
  canvas.ellipse(574, 125, 10, 10, palette.mint);
  drawing.draw(canvas);
  canvas.demoBadge();
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(WIDTH, 0);
  ihdr.writeUInt32BE(HEIGHT, 4);
  ihdr[8] = 8; // Eight bits per channel.
  ihdr[9] = 2; // RGB, no alpha.
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('tEXt', Buffer.from(`Title\0Four-Lane Auction demo ${name}`, 'latin1')),
    chunk('tEXt', Buffer.from('License\0CC0-1.0 https://creativecommons.org/publicdomain/zero/1.0/', 'latin1')),
    chunk('tEXt', Buffer.from('Source\0simulator/seed/generate-images.mjs; original geometric illustration', 'latin1')),
    chunk('IDAT', deflateSync(canvas.rawPngRows(), { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

export async function generateImages() {
  const directory = new URL('./assets/', import.meta.url);
  await mkdir(directory, { recursive: true });
  const results = [];
  for (const name of Object.keys(drawings)) {
    const png = createDemoImage(name);
    await writeFile(new URL(`${name}.png`, directory), png);
    results.push({ file: `${name}.png`, width: WIDTH, height: HEIGHT, bytes: png.length,
      sha256: createHash('sha256').update(png).digest('hex') });
  }
  return results;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  console.log(JSON.stringify(await generateImages(), null, 2));
}
