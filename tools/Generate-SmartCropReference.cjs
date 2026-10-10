// Development-only oracle. Run: node tools/Generate-SmartCropReference.cjs <pinned smartcrop.js> <output.tsv>
// Source revision: 0e207ed910625f91b07d35865c71a9621a63ebdb, https://github.com/jwagner/smartcrop.js
const fs = require('fs');
const smartcrop = require(require('path').resolve(process.argv[2]));
const cases = ['detail', 'skin', 'saturation', 'thirds-off', 'scales', 'portrait', 'odd-size', 'boost', 'overlap-boost', 'black'];
function pixel(x, y, mode) {
  if (mode === 'black') return [0, 0, 0];
  if (mode === 'skin') return x > 150 && y > 40 && y < 155 ? [210, 153, 118] : [32, 40, 45];
  if (mode === 'saturation') return x > 145 && y > 25 && y < 145 ? [220, 10, 35] : [55, 55, 55];
  const v = (x * 17 + y * 31 + (x ^ y) * 7) % 256;
  return [v, (x * 3 + y * 5) % 256, (x * 7 + y * 11) % 256];
}
(async () => {
  const rows = ['# Original smartcrop.js 0e207ed910625f91b07d35865c71a9621a63ebdb; prescale=false; ARGB recipes in SmartCropTest.',
    '# case\tx\ty\twidth\theight\tdetail\tskin\tsaturation\tboost\ttotal'];
  for (const name of cases) {
    const width = name === 'portrait' ? 128 : name === 'odd-size' ? 259 : 256;
    const height = name === 'portrait' ? 256 : name === 'odd-size' ? 173 : 192;
    const image = { width, height, data: new Uint8ClampedArray(width * height * 4) };
    for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) image.data.set([...pixel(x, y, name), 255], (y * width + x) * 4);
    const options = { width: 80, height: 80, prescale: false, imageOperations: {
      open: image => Promise.resolve(image), getData: image => Promise.resolve(image),
      resample: () => { throw Error('Prescaling must be disabled in the exact reference'); }
    }};
    if (name === 'thirds-off') options.ruleOfThirds = false;
    if (name === 'scales') options.minScale = .75;
    if (name === 'portrait') options.height = 48;
    if (name === 'boost') options.boost = [{ x: 206, y: 72, width: 24, height: 40, weight: 1 }];
    if (name === 'overlap-boost') options.boost = [{ x: 10, y: 30, width: 20, height: 60, weight: .6 }, { x: 20, y: 50, width: 24, height: 60, weight: .7 }];
    const c = (await smartcrop.crop(image, options)).topCrop;
    rows.push([name, c.x, c.y, c.width, c.height, c.score.detail, c.score.skin, c.score.saturation, c.score.boost, c.score.total].join('\t'));
  }
  fs.writeFileSync(process.argv[3], rows.join('\n') + '\n', 'utf8');
})();
