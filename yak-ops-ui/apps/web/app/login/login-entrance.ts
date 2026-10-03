// Login-only geometry. The scene owns elapsed time; this sampler has no DOM or timers.
export const LOGIN_ENTRANCE_COMPLETE_EVENT = "yak-login-entrance-complete";
export const LOGIN_ENTRANCE_DURATION_MS = 1120;
export const ORANGE_BODY_TOP_Y = 376;
export const PURPLE_DEFAULT_BODY_PATH =
  "M212 550 C212 430 212 260 212 102 L404 102 C404 260 404 430 404 550 Z";
export const BLACK_DEFAULT_BODY_PATH = "M342 550V242H464V550Z";
export const YELLOW_BODY_PATH =
  "M450 550V407C450 352 481 318 524 318C565 318 590 349 590 404C590 456 590 505 590 550Z";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function point(value: number) {
  return Number(value.toFixed(4));
}

function mix(a: number, b: number, progress: number) {
  return a + (b - a) * progress;
}

function ramp(time: number, start: number, end: number) {
  const value = clamp((time - start) / (end - start), 0, 1);
  return value * value * (3 - 2 * value);
}

function stops(time: number, values: readonly (readonly [number, number])[]) {
  for (let index = 1; index < values.length; index += 1) {
    const [start, from] = values[index - 1];
    const [end, to] = values[index];
    if (time <= end) return mix(from, to, ramp(time, start, end));
  }
  return values[values.length - 1][1];
}

// Matrices use root SVG coordinates, independent of a changing path/face bounding box.
function rotation(x: number, y: number, degrees: number, dx = 0, dy = 0) {
  const angle = (degrees * Math.PI) / 180;
  const c = Math.cos(angle);
  const s = Math.sin(angle);
  return `matrix(${c},${s},${-s},${c},${x - c * x + s * y + dx},${y - s * x - c * y + dy})`;
}

function groundScale(scale: number) {
  return `matrix(${scale},0,0,${scale},${240 * (1 - scale)},${550 * (1 - scale)})`;
}

// This is also the ordinary/input body builder, so the last entrance frame has one owner.
export function buildOrangeBodyPath(x: number, y: number, inputMix = 0) {
  const topY = ORANGE_BODY_TOP_Y - inputMix * 8;
  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  const pointerLift = Math.max(0, -vertical) * 8;
  const crownX = 244 + horizontal * 6;
  const crownY = topY - pointerLift;
  const sideY = Number((topY + (550 - topY) * 0.42).toFixed(2));
  const leftLift = Math.max(-horizontal, 0) * 4 + pointerLift * 0.5;
  const rightLift = Math.max(horizontal, 0) * 4 + pointerLift * 0.5;
  const round = (value: number) => Number(value.toFixed(2));
  return [
    "M65 550",
    `C65 ${sideY} ${round(142 + horizontal * 2)} ${round(topY - leftLift)}`,
    `${round(crownX)} ${round(crownY)}`,
    `C${round(346.05 + horizontal * 2)} ${round(topY - rightLift)} 415 ${sideY} 415 550`,
    "Z",
  ].join(" ");
}

const ORANGE_REST_PATH = buildOrangeBodyPath(0, 0);
const IDENTITY = "matrix(1,0,0,1,0,0)";

type EntrancePose = {
  path: string;
  transform: string;
  faceTransform: string;
  faceOpacity: number;
};

export type YellowEntrancePose = {
  path: string;
  eyeX: number;
  eyeY: number;
  mouth: string;
  extraEyeX: number;
  extraEyeOpacity: number;
};

export type LoginEntrancePose = {
  complete: boolean;
  purple: EntrancePose;
  black: EntrancePose;
  orange: EntrancePose;
  yellow: YellowEntrancePose;
};

function samplePurple(time: number): EntrancePose {
  const height = stops(time, [
    [0, 192],
    [510, 192],
    [735, 464],
    [880, 446],
    [1080, 448],
  ]);
  const angle = stops(time, [
    [0, -42],
    [200, 7],
    [320, -3],
    [450, 0],
  ]);
  const dx = stops(time, [
    [0, -100],
    [210, 0],
  ]);
  const dy = stops(time, [
    [0, -35],
    [210, 0],
  ]);
  return {
    path: time >= 1080 ? PURPLE_DEFAULT_BODY_PATH : `M212 550V${point(550 - height)}H404V550Z`,
    transform: rotation(308, 550 - height / 2, angle, dx, dy),
    faceTransform: `translate(0,${point(448 - height)})`,
    faceOpacity: ramp(time, 560, 670),
  };
}

function sampleBlack(time: number): EntrancePose {
  const angle = stops(time, [
    [0, -180],
    [200, -180],
    [330, -112],
    [400, -72],
    [460, -30],
    [530, 0],
  ]);
  const dx = stops(time, [
    [0, 30],
    [210, 30],
    [340, 20],
    [460, -10],
    [530, 0],
  ]);
  const dy = stops(time, [
    [0, -485],
    [210, -485],
    [340, -295],
    [445, -165],
    [530, 0],
  ]);
  const bend = stops(time, [
    [0, 0],
    [210, 0],
    [335, -70],
    [410, -42],
    [530, 0],
  ]);
  const path =
    bend === 0
      ? BLACK_DEFAULT_BODY_PATH
      : [
        "M342 550",
        `C342 448 ${point(342 + bend * 0.45)} 345 ${point(342 + bend)} 242`,
        `H${point(464 + bend)}`,
        `C${point(464 + bend * 0.45)} 345 464 448 464 550Z`,
      ].join(" ");
  const lean = stops(time, [
    [0, 0],
    [530, 0],
    [590, -14],
    [740, 4],
    [990, 0],
  ]);
  const skew = Math.tan((lean * Math.PI) / 180);
  return {
    path,
    transform:
      time < 530
        ? rotation(403, 396, angle, dx, dy)
        : `matrix(1,0,${skew},1,${-550 * skew},0)`,
    faceTransform: rotation(403.5, 278, bend * 0.09, bend * 0.81),
    faceOpacity: 1,
  };
}

// Four cubic segments share topology from a flying bean to a grounded half-disc.
// The lower two segments flatten continuously before the first grounded frame.
function orangeMorph(
  cx: number,
  cy: number,
  rx: number,
  ry: number,
  angle: number,
  morph: number,
) {
  const k = 0.55228475;
  const bean = [
    [-1, 0],
    [-1, -k],
    [-k, -1],
    [0, -1],
    [k, -1],
    [1, -k],
    [1, 0],
    [1, k],
    [k, 1],
    [0, 1],
    [-k, 1],
    [-1, k],
    [-1, 0],
  ];
  const dome = [
    [-1, 0],
    [-1, -0.58],
    [-0.56, -1],
    [4 / 175, -1],
    [0.606, -1],
    [1, -0.58],
    [1, 0],
    [0.6, 0],
    [0.2, 0],
    [0, 0],
    [-0.2, 0],
    [-0.6, 0],
    [-1, 0],
  ];
  const radians = (angle * Math.PI) / 180;
  const points = bean.map(([x, y], index) => {
    const px = mix(x, dome[index][0], morph) * rx;
    const py = mix(y, dome[index][1], morph) * ry;
    return `${point(cx + px * Math.cos(radians) - py * Math.sin(radians))} ${point(cy + px * Math.sin(radians) + py * Math.cos(radians))}`;
  });
  return `M${points[0]} C${points.slice(1, 4).join(" ")} C${points.slice(4, 7).join(" ")} C${points.slice(7, 10).join(" ")} C${points.slice(10, 13).join(" ")}Z`;
}

function sampleOrange(time: number): EntrancePose {
  if (time < 430) {
    const p = time / 430;
    const cx = (1 - p) ** 2 * 90 + 2 * (1 - p) * p * 125 + p ** 2 * 240;
    const cy = (1 - p) ** 2 * 520 + 2 * (1 - p) * p * 160 + p ** 2 * 550;
    const morph = ramp(time, 385, 430);
    const angle = stops(time, [
      [0, 8],
      [235, -85],
      [380, 0],
      [430, 0],
    ]);
    return {
      path: orangeMorph(
        cx,
        cy,
        mix(15, 175 * 0.35, morph),
        mix(28, 174 * 0.35, morph),
        angle,
        morph,
      ),
      transform: IDENTITY,
      faceTransform: groundScale(0.35),
      faceOpacity: 0,
    };
  }
  const scale = stops(time, [
    [430, 0.35],
    [520, 0.8],
    [620, 1.12],
    [790, 1.02],
    [1010, 1],
  ]);
  return {
    path:
      time >= 1010 ? ORANGE_REST_PATH : orangeMorph(240, 550, 175 * scale, 174 * scale, 0, 1),
    transform: IDENTITY,
    faceTransform: groundScale(scale),
    faceOpacity: ramp(time, 450, 545),
  };
}

function sampleYellow(time: number): YellowEntrancePose {
  const shift = stops(time, [
    [0, 132],
    [500, 132],
    [745, -10],
    [880, 3],
    [1120, 0],
  ]);
  const shoulder = 404 + shift;
  const path =
    time >= 1120
      ? YELLOW_BODY_PATH
      : `M450 550V${point(407 + shift)}C450 ${point(352 + shift)} 481 ${point(318 + shift)} 524 ${point(318 + shift)}C565 ${point(318 + shift)} 590 ${point(349 + shift)} 590 ${point(shoulder)}C590 ${point(mix(shoulder, 550, 52 / 146))} 590 ${point(mix(shoulder, 550, 101 / 146))} 590 550Z`;
  const turn = ramp(time, 520, 675);
  const eyeX = stops(time, [
    [0, 520],
    [540, 520],
    [640, 480],
    [760, 490],
    [1120, 520],
  ]);
  const mouthX = stops(time, [
    [0, 438],
    [520, 446],
    [610, 450],
    [690, 498],
    [775, 530],
    [1120, 480],
  ]);
  const mouthY = 411 + shift - 14 * (1 - turn);
  return {
    path,
    eyeX,
    eyeY: 376 + shift,
    mouth: `M${point(mouthX)} ${point(mouthY - 18 * (1 - turn))}L${point(mouthX + 80)} ${point(mouthY + 18 * (1 - turn))}`,
    extraEyeX: stops(time, [
      [0, 560],
      [620, 560],
      [800, 598],
    ]),
    extraEyeOpacity: ramp(time, 600, 640) * (1 - ramp(time, 730, 800)),
  };
}

export function sampleLoginEntrance(elapsedMs: number): LoginEntrancePose {
  const time = Number.isFinite(elapsedMs)
    ? clamp(elapsedMs, 0, LOGIN_ENTRANCE_DURATION_MS)
    : LOGIN_ENTRANCE_DURATION_MS;
  return {
    complete: time >= LOGIN_ENTRANCE_DURATION_MS,
    purple: samplePurple(time),
    black: sampleBlack(time),
    orange: sampleOrange(time),
    yellow: sampleYellow(time),
  };
}

export const LOGIN_ENTRANCE_START = sampleLoginEntrance(0);
