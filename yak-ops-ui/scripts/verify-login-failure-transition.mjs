import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

// No browser shim or new test dependency. This checks geometry, not DOM/animation acceptance.
const source = readFileSync(
  new URL("../apps/web/app/login/login-failure-transition.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
});
const { normalizeAuthoredLoginPath: normalize, mixFailureSnapshot: mix } = await import(
  `data:text/javascript;base64,${Buffer.from(outputText).toString("base64")}`
);

const rectangle = normalize("M342 550V242H464V550Z");
const shortened = normalize("M354 550V305H482V550Z");
const purple = normalize("M212 550C212 430 212 260 212 102L404 102C404 260 404 430 404 550Z", true);
const failure = normalize("M212 550L220 377L168 250L206 74L410 117L386 231L418 303L404 550Z", true);
for (const path of [rectangle, shortened, purple, failure]) {
  assert.equal(path.length, 194, "every contour has a fixed cubic topology");
  assert(path.every(Number.isFinite));
  assert.deepEqual(path.slice(0, 2), path.slice(-2), "closed ground edge is preserved");
  assert.equal(path[1], 550);
  assert.equal(path.at(-1), 550);
}
assert(Math.abs(Math.min(...purple.filter((_, i) => i % 2)) - 102) < 1e-9);
assert.equal(Math.max(...purple.filter((_, i) => i % 2)), 550);
// Quadratics, reflected cubics and horizontal mouths keep their actual endpoints.
for (const d of ["M0 0Q5 8 10 0", "M0 0C2 4 3 5 5 5S8 4 10 0", "M0 0H10"]) {
  assert.deepEqual(normalize(d).slice(0, 2), [0, 0]);
  assert.deepEqual(normalize(d).slice(-2), [10, 0]);
}
assert.throws(() => normalize("M0 0A10 10 0 0 1 20 20"), /Unsupported/);
assert.throws(() => normalize("M0 0C1"), /Invalid/);

const pathPose = (points) => ({
  points,
  opacity: 1,
  fill: "#171717",
  fillOpacity: 1,
  stroke: "#171717",
  strokeOpacity: 0,
  strokeWidth: 1,
});
const ellipse = (x, y) => ({ matrix: [5, 0, 0, 5, x, y], opacity: 1 });
const snapshot = (x, body = rectangle) => [
  {
    body: pathPose(body),
    eyes: [{ white: ellipse(x, 280), pupil: ellipse(x + 1, 280), lid: [] }],
    mouths: [pathPose(normalize(`M${x - 10} 300Q${x} 310 ${x + 10} 300`))],
  },
];
const from = snapshot(420, shortened);
const to = snapshot(490);
const original = JSON.stringify([from, to]);
assert.equal(mix(from, to, 0), from, "the first frame is the captured drawing");
assert.equal(mix(from, to, 1), to, "the last frame is the live normal rig, not a fallback");
for (let i = 0; i <= 100; i += 1) {
  const p = i / 100;
  const frame = mix(from, to, p * p * (3 - 2 * p));
  const x = frame[0].eyes[0].white.matrix[4];
  assert(x >= 420 && x <= 490, "recovery cannot detour through the neutral center");
  assert.equal(frame[0].body.points[1], 550, "ground remains fixed while height changes");
  assert(frame[0].body.points.every(Number.isFinite));
}
assert.equal(JSON.stringify([from, to]), original, "blending must not mutate either rig");
const moving = (t) => mix(from, snapshot(490 + 20 * t), t * t * (3 - 2 * t));
const h = 0.00001;
const speed = (moving(1)[0].eyes[0].white.matrix[4] - moving(1 - h)[0].eyes[0].white.matrix[4]) / h;
assert(Math.abs(speed - 20) < 0.01, "handoff retains the live target velocity");
const interrupted = moving(0.45);
assert.equal(
  mix(interrupted, snapshot(320), 0),
  interrupted,
  "rapid retargeting starts at the last drawn pose",
);
const noMouth = [{ ...to[0], mouths: [], eyes: [] }];
const fading = mix(from, noMouth, 0.5)[0];
assert.equal(fading.mouths[0].opacity, 0.5);
assert.equal(fading.eyes[0].white.opacity, 0.5);
assert.equal(mix(from, noMouth, 1), noMouth);
console.log(
  "Login failure geometry checks passed (topology, ground, endpoints, direct recovery, moving target, interruption).",
);
