/** Failure-only vector handoff. The existing rigs remain the owners of their end poses. */
export const LOGIN_FAILURE_TRANSITION_END = "yak-login-failure-transition-end";
const ENTER_MS = 180;
const RECOVER_MS = 280;
const SEGMENTS = 32;
const NS = "http://www.w3.org/2000/svg";
const CHARACTERS = ["purple", "black", "yellow", "orange"] as const;
type Character = (typeof CHARACTERS)[number];
type Point = [number, number];
type Cubic = [Point, Point, Point, Point];
type Matrix = [number, number, number, number, number, number];
type PathPose = {
  points: number[];
  opacity: number;
  fill: string;
  fillOpacity: number;
  stroke: string;
  strokeOpacity: number;
  strokeWidth: number;
};
type EllipsePose = { matrix: Matrix; opacity: number };
type EyePose = { white: EllipsePose; pupil: EllipsePose; lid: PathPose[] };
type CharacterPose = { body: PathPose; eyes: EyePose[]; mouths: PathPose[] };
export type FailureSnapshot = CharacterPose[];
type Phase = "none" | "enter" | "hold" | "recover" | "frozen";

const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const point = (a: Point, b: Point, t: number): Point => [lerp(a[0], b[0], t), lerp(a[1], b[1], t)];

function split(c: Cubic, t: number): [Cubic, Cubic] {
  const a = point(c[0], c[1], t);
  const b = point(c[1], c[2], t);
  const d = point(c[2], c[3], t);
  const e = point(a, b, t);
  const f = point(b, d, t);
  const g = point(e, f, t);
  return [
    [c[0], a, e, g],
    [g, f, d, c[3]],
  ];
}

function divide(c: Cubic, count: number): Cubic[] {
  const parts: Cubic[] = [];
  let rest = c;
  for (let remaining = count; remaining > 1; remaining -= 1) {
    const [first, next] = split(rest, 1 / remaining);
    parts.push(first);
    rest = next;
  }
  return [...parts, rest];
}

// Only the authored login paths are accepted (absolute M/L/H/V/C/S/Q/Z). No general SVG parser.
function pathCurves(d: string): Cubic[] {
  const tokens = d.match(/[a-zA-Z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?/g) ?? [];
  const curves: Cubic[] = [];
  let position: Point = [0, 0];
  let start = position;
  let command = "";
  let previousCommand = "";
  let index = 0;
  const number = () => {
    const value = Number(tokens[index++]);
    if (!Number.isFinite(value)) throw new Error("Invalid login pose coordinate");
    return value;
  };
  const readPoint = (): Point => [number(), number()];
  const line = (to: Point) => {
    curves.push([position, point(position, to, 1 / 3), point(position, to, 2 / 3), to]);
    position = to;
  };
  while (index < tokens.length) {
    if (/^[A-Za-z]$/.test(tokens[index])) command = tokens[index++];
    switch (command) {
      case "M":
        position = readPoint();
        start = position;
        command = "L";
        break;
      case "L":
        line(readPoint());
        break;
      case "H":
        line([number(), position[1]]);
        break;
      case "V":
        line([position[0], number()]);
        break;
      case "C": {
        const a = readPoint();
        const b = readPoint();
        const end = readPoint();
        curves.push([position, a, b, end]);
        position = end;
        break;
      }
      case "S": {
        const last = curves.at(-1);
        const a =
          last && (previousCommand === "C" || previousCommand === "S")
            ? point(last[2], position, 2)
            : position;
        const b = readPoint();
        const end = readPoint();
        curves.push([position, a, b, end]);
        position = end;
        break;
      }
      case "Q": {
        const control = readPoint();
        const end = readPoint();
        curves.push([position, point(position, control, 2 / 3), point(end, control, 2 / 3), end]);
        position = end;
        break;
      }
      case "Z":
        if (position[0] !== start[0] || position[1] !== start[1]) line(start);
        command = "";
        break;
      default:
        throw new Error(`Unsupported login pose command: ${command}`);
    }
    previousCommand = command;
  }
  return curves;
}

function ellipseCurves(node: SVGElement): Cubic[] {
  const cx = Number(node.getAttribute("cx"));
  const cy = Number(node.getAttribute("cy"));
  const rx = Number(node.getAttribute("rx") ?? node.getAttribute("r"));
  const ry = Number(node.getAttribute("ry") ?? node.getAttribute("r"));
  const k = 0.5522847498307936;
  return [
    [
      [cx - rx, cy],
      [cx - rx, cy - ry * k],
      [cx - rx * k, cy - ry],
      [cx, cy - ry],
    ],
    [
      [cx, cy - ry],
      [cx + rx * k, cy - ry],
      [cx + rx, cy - ry * k],
      [cx + rx, cy],
    ],
    [
      [cx + rx, cy],
      [cx + rx, cy + ry * k],
      [cx + rx * k, cy + ry],
      [cx, cy + ry],
    ],
    [
      [cx, cy + ry],
      [cx - rx * k, cy + ry],
      [cx - rx, cy + ry * k],
      [cx - rx, cy],
    ],
  ];
}

function relativeMatrix(node: SVGGraphicsElement, inverse: DOMMatrix): DOMMatrix {
  const matrix = node.getScreenCTM();
  if (!matrix) throw new Error("Login pose has no SVG matrix");
  return inverse.multiply(matrix);
}

function opacity(node: SVGElement, branch: SVGElement) {
  let value = 1;
  // Branch opacity only selects the active renderer; descendant opacity still describes the pose.
  for (
    let current: Element | null = node;
    current && current !== branch;
    current = current.parentElement
  ) {
    const style = getComputedStyle(current);
    if (style.display === "none" || style.visibility === "hidden") return 0;
    value *= Number(style.opacity);
  }
  return value;
}

function normalizeCurves(curves: Cubic[], purple = false): number[] {
  // Match left/right edge sections, not equal perimeter distances. Subdivision is exact.
  if (purple && curves.length === 4) {
    curves = [...divide(curves[0], 3), curves[1], ...divide(curves[2], 3), curves[3]];
  }
  if (!curves.length || curves.length > SEGMENTS)
    throw new Error("Unsupported login pose topology");
  const normalized = curves.flatMap((curve, i) =>
    divide(curve, Math.floor(SEGMENTS / curves.length) + (i < SEGMENTS % curves.length ? 1 : 0)),
  );
  return [normalized[0][0], ...normalized.flatMap((c) => c.slice(1))].flat();
}

/** Canonical cubic coordinates for the authored paths, also used by the focused geometry check. */
export function normalizeAuthoredLoginPath(d: string, purple = false): number[] {
  return normalizeCurves(pathCurves(d), purple);
}

function readPath(
  node: SVGGraphicsElement,
  branch: SVGElement,
  inverse: DOMMatrix,
  body?: Character,
): PathPose {
  const coordinates =
    node.tagName.toLowerCase() === "path"
      ? normalizeAuthoredLoginPath(node.getAttribute("d") ?? "", body === "purple")
      : normalizeCurves(ellipseCurves(node));
  const matrix = relativeMatrix(node, inverse);
  const points = coordinates.map((_, i) => {
    const x = coordinates[i - (i % 2)];
    const y = coordinates[i - (i % 2) + 1];
    return i % 2 === 0
      ? matrix.a * x + matrix.c * y + matrix.e
      : matrix.b * x + matrix.d * y + matrix.f;
  });
  const style = getComputedStyle(node);
  return {
    points,
    opacity: opacity(node, branch),
    fill: style.fill === "none" ? "#171717" : style.fill,
    fillOpacity: style.fill === "none" ? 0 : Number(style.fillOpacity),
    stroke: style.stroke === "none" ? "#171717" : style.stroke,
    strokeOpacity: style.stroke === "none" ? 0 : Number(style.strokeOpacity),
    strokeWidth:
      parseFloat(style.strokeWidth) *
      Math.sqrt(Math.abs(matrix.a * matrix.d - matrix.b * matrix.c)),
  };
}

function readEllipse(
  node: SVGGraphicsElement,
  branch: SVGElement,
  inverse: DOMMatrix,
): EllipsePose {
  const matrix = relativeMatrix(node, inverse)
    .translate(Number(node.getAttribute("cx")), Number(node.getAttribute("cy")))
    .scale(
      Number(node.getAttribute("rx") ?? node.getAttribute("r")),
      Number(node.getAttribute("ry") ?? node.getAttribute("r")),
    );
  return {
    matrix: [matrix.a, matrix.b, matrix.c, matrix.d, matrix.e, matrix.f],
    opacity: opacity(node, branch),
  };
}

function capture(scene: HTMLElement, failure: boolean): FailureSnapshot | null {
  const svg = scene.querySelector("svg");
  const matrix = svg?.getScreenCTM();
  if (
    !matrix ||
    !scene.getClientRects().length ||
    Math.abs(matrix.a * matrix.d - matrix.b * matrix.c) < 1e-8
  )
    return null;
  const inverse = matrix.inverse();
  try {
    return CHARACTERS.map((character) => {
      const root = scene.querySelector<SVGGElement>(`[data-character="${character}"]`)!;
      const branch = root.querySelector<SVGGElement>(
        failure ? "[data-failure-pose]" : "[data-character-motion]",
      )!;
      const body = branch.querySelector<SVGPathElement>(
        failure ? "[data-failure-body]" : `[data-${character}-body-path]`,
      )!;
      const whiteEyes = character === "purple" || character === "black";
      const selector =
        failure || whiteEyes
          ? "[data-pose-eye]"
          : character === "yellow"
            ? "[data-yellow-eye], [data-yellow-entrance-eye]"
            : ".yak-login-character--orange__eyes-open circle";
      const eyes = Array.from(branch.querySelectorAll<SVGGraphicsElement>(selector))
        .filter((eye) => character === "orange" || opacity(eye, branch) > 0)
        .map((eye, index) => {
          const shape = readEllipse(eye, branch, inverse);
          const pupil = whiteEyes
            ? eye.parentElement!.querySelector<SVGGraphicsElement>("[data-pose-pupil]")
            : null;
          const lid =
            !failure && character === "orange"
              ? branch.querySelectorAll<SVGPathElement>(
                  ".yak-login-character--orange__eyes-blink path",
                )[index]
              : null;
          return {
            white: { ...shape, opacity: whiteEyes ? shape.opacity : 0 },
            pupil: pupil ? readEllipse(pupil, branch, inverse) : shape,
            lid: lid ? [readPath(lid, branch, inverse)] : [],
          };
        });
      const mouths = Array.from(
        branch.querySelectorAll<SVGGraphicsElement>(
          failure ? "[data-failure-mouth]" : ".yak-login-character__mouth",
        ),
      )
        .filter((mouth) => opacity(mouth, branch) > 0.00001)
        .map((mouth) => readPath(mouth, branch, inverse));
      return { body: readPath(body, branch, inverse, character), eyes, mouths };
    });
  } catch {
    // A hidden/detached scene settles to its authored endpoint instead of leaving a stale overlay.
    return null;
  }
}

function mixPath(a: PathPose, b: PathPose, t: number): PathPose {
  return {
    points: a.points.map((v, i) => lerp(v, b.points[i], t)),
    opacity: lerp(a.opacity, b.opacity, t),
    fill: b.fillOpacity > 0 ? b.fill : a.fill,
    fillOpacity: lerp(a.fillOpacity, b.fillOpacity, t),
    stroke: b.strokeOpacity > 0 ? b.stroke : a.stroke,
    strokeOpacity: lerp(a.strokeOpacity, b.strokeOpacity, t),
    strokeWidth: lerp(a.strokeWidth, b.strokeWidth, t),
  };
}
function mixPaths(a: PathPose[], b: PathPose[], t: number): PathPose[] {
  return Array.from({ length: Math.max(a.length, b.length) }, (_, i) => {
    const from = a[i] ?? { ...b[i], opacity: 0 };
    const to = b[i] ?? { ...a[i], opacity: 0 };
    return mixPath(from, to, t);
  });
}
function mixEllipse(a: EllipsePose, b: EllipsePose, t: number): EllipsePose {
  return {
    matrix: a.matrix.map((v, i) => lerp(v, b.matrix[i], t)) as Matrix,
    opacity: lerp(a.opacity, b.opacity, t),
  };
}

/** A live destination and zero-slope blend endpoints give ordinary motion a continuous handoff. */
export function mixFailureSnapshot(
  from: FailureSnapshot,
  to: FailureSnapshot,
  t: number,
): FailureSnapshot {
  if (t <= 0) return from;
  if (t >= 1) return to;
  return from.map((a, i) => {
    const b = to[i];
    const eyes = Array.from({ length: Math.max(a.eyes.length, b.eyes.length) }, (_, j) => {
      const blank = (eye: EyePose): EyePose => ({
        white: { ...eye.white, opacity: 0 },
        pupil: { ...eye.pupil, opacity: 0 },
        lid: [],
      });
      const x = a.eyes[j] ?? blank(b.eyes[j]);
      const y = b.eyes[j] ?? blank(a.eyes[j]);
      return {
        white: mixEllipse(x.white, y.white, t),
        pupil: mixEllipse(x.pupil, y.pupil, t),
        lid: mixPaths(x.lid, y.lid, t),
      };
    });
    return { body: mixPath(a.body, b.body, t), eyes, mouths: mixPaths(a.mouths, b.mouths, t) };
  });
}

function element<K extends keyof SVGElementTagNameMap>(tag: K, parent: SVGElement) {
  const node = document.createElementNS(NS, tag);
  parent.append(node);
  return node;
}
function drawPath(node: SVGPathElement, pose: PathPose) {
  const n = (v: number) => String(Number(v.toFixed(4)));
  node.setAttribute(
    "d",
    `M${pose.points.slice(0, 2).map(n).join(" ")} C${pose.points.slice(2).map(n).join(" ")}`,
  );
  for (const [name, value] of Object.entries({
    opacity: pose.opacity,
    fill: pose.fill,
    "fill-opacity": pose.fillOpacity,
    stroke: pose.stroke,
    "stroke-opacity": pose.strokeOpacity,
    "stroke-width": pose.strokeWidth,
  }))
    node.setAttribute(name, String(value));
  node.setAttribute("stroke-linecap", "round");
}
function drawEllipse(node: SVGEllipseElement, pose: EllipsePose) {
  node.setAttribute("rx", "1");
  node.setAttribute("ry", "1");
  node.setAttribute("transform", `matrix(${pose.matrix.join(" ")})`);
  node.setAttribute("opacity", String(pose.opacity));
}

export function createLoginFailureTransition(scene: HTMLElement, id: string) {
  const nodes = CHARACTERS.map((character) => {
    const group = scene.querySelector<SVGGElement>(
      `[data-character="${character}"] [data-failure-bridge]`,
    )!;
    const body = element("path", group);
    body.setAttribute("data-transition-body", character);
    const eyes = [0, 1].map((index) => {
      const clip = element("clipPath", element("defs", group));
      clip.id = `${id}-${character}-${index}`;
      clip.setAttribute("clipPathUnits", "userSpaceOnUse");
      const mask = element("ellipse", clip);
      const white = element("ellipse", group);
      white.setAttribute("fill", "#fff");
      const pupilGroup = element("g", group);
      pupilGroup.setAttribute("clip-path", `url(#${clip.id})`);
      const pupil = element("ellipse", pupilGroup);
      pupil.setAttribute("fill", "#171717");
      const lid = element("g", group);
      return { mask, white, pupil, lid };
    });
    const mouths = element("g", group);
    return { group, body, eyes, mouths };
  });
  let phase: Phase = "none";
  let source: FailureSnapshot | null = null;
  let painted: FailureSnapshot | null = null;
  let startedAt = 0;
  const paths = (parent: SVGElement, poses: PathPose[]) => {
    while (parent.children.length > poses.length) parent.lastElementChild!.remove();
    poses.forEach((pose, i) =>
      drawPath((parent.children[i] as SVGPathElement | undefined) ?? element("path", parent), pose),
    );
  };
  const draw = (snapshot: FailureSnapshot) => {
    painted = snapshot;
    snapshot.forEach((pose, i) => {
      const node = nodes[i];
      drawPath(node.body, pose.body);
      node.eyes.forEach((eye, j) => {
        const value = pose.eyes[j];
        if (value) {
          drawEllipse(eye.mask, { ...value.white, opacity: 1 });
          drawEllipse(eye.white, value.white);
          drawEllipse(eye.pupil, value.pupil);
        } else {
          eye.white.setAttribute("opacity", "0");
          eye.pupil.setAttribute("opacity", "0");
        }
        paths(eye.lid, value?.lid ?? []);
      });
      paths(node.mouths, pose.mouths);
    });
  };
  const setPhase = (next: Phase) => {
    phase = next;
    scene.dataset.failurePhase = next;
  };
  const settle = (failure: boolean) => {
    const wasActive = phase !== "none";
    setPhase(failure ? "hold" : "none");
    source = null;
    painted = null;
    if (wasActive && !failure) scene.dispatchEvent(new Event(LOGIN_FAILURE_TRANSITION_END));
  };
  return {
    captureVisible: () => painted ?? capture(scene, phase === "hold"),
    active: () => phase !== "none" && phase !== "hold",
    settle,
    begin(next: "enter" | "recover" | "frozen", snapshot: FailureSnapshot | null, now: number) {
      if (!snapshot) {
        settle(next === "enter");
        return;
      }
      source = snapshot;
      startedAt = now;
      draw(snapshot);
      setPhase(next);
    },
    paint(now: number) {
      if (!source || phase === "frozen") return;
      const entering = phase === "enter";
      const progress = Math.max(
        0,
        Math.min(1, (now - startedAt) / (entering ? ENTER_MS : RECOVER_MS)),
      );
      const target = capture(scene, entering);
      if (!target) {
        settle(entering);
        return;
      }
      draw(mixFailureSnapshot(source, target, progress * progress * (3 - 2 * progress)));
      if (progress === 1) settle(entering);
    },
    dispose() {
      nodes.forEach(({ group }) => group.replaceChildren());
      delete scene.dataset.failurePhase;
      source = null;
      painted = null;
    },
  };
}
