import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import ts from "typescript";

// Compile only the login-local modules. No browser shim, credential fixture or new test framework.
const cache = new Map();
function moduleUrl(name) {
  if (cache.has(name)) return cache.get(name);
  const source = readFileSync(new URL(`../apps/web/app/login/${name}.ts`, import.meta.url), "utf8");
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  });
  const code = outputText.replace(
    /from ["']\.\/([^"']+)["']/g,
    (_, dependency) => `from "${moduleUrl(dependency)}"`,
  );
  const url = `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`;
  cache.set(name, url);
  return url;
}
const { normalizeAuthoredLoginPath: normalize, mixLoginPoseSnapshot: mix } = await import(
  moduleUrl("login-pose-snapshot")
);
const { LOGIN_SUCCESS_POSES: poses, ORANGE_HAPPY_MOUTH_PATH: orangeMouth } = await import(
  moduleUrl("login-success-pose")
);
const { sampleLoginSuccessTransition: sample } = await import(
  moduleUrl("login-success-transition")
);
const { LOGIN_SUCCESS_MOTION_MS: windowMs } = await import(moduleUrl("login-interaction"));
const { PURPLE_DEFAULT_BODY_PATH, BLACK_DEFAULT_BODY_PATH, YELLOW_BODY_PATH, buildOrangeBodyPath } =
  await import(moduleUrl("login-entrance"));

assert.deepEqual(Object.keys(poses), ["purple", "black", "yellow", "orange"]);
assert.deepEqual(
  Object.values(poses).map((pose) => pose.body),
  [PURPLE_DEFAULT_BODY_PATH, BLACK_DEFAULT_BODY_PATH, YELLOW_BODY_PATH, buildOrangeBodyPath(0, 0)],
);
assert.equal(poses.black.mouth, null, "black remains mouthless");
assert.equal(poses.yellow.eyes.length, 1, "yellow remains a side profile");
assert.equal(poses.orange.mouth.path, orangeMouth, "orange retains its ordinary open smile");
for (const [name, pose] of Object.entries(poses)) {
  const contour = normalize(pose.body, name === "purple");
  assert.equal(contour.length, 194);
  assert.equal(contour[1], 550);
  assert.equal(contour.at(-1), 550);
  assert.deepEqual(contour.slice(0, 2), contour.slice(-2));
  for (const eye of pose.eyes) {
    assert.equal(Boolean(eye.lid), name === "orange", "only orange has closed smile eyes");
    if (eye.pupilRadius !== undefined) assert(eye.pupilRadius < eye.radius);
  }
}
const yellowMouth = normalize(poses.yellow.mouth.path);
assert(yellowMouth[0] > poses.yellow.eyes[0].x, "mouth starts to the right of the eye");
assert(yellowMouth.at(-2) > 590, "long mouth extends beyond the body, without a face clip");
assert(yellowMouth.at(-1) < yellowMouth[1], "only the far tip lifts");
const purpleMouth = normalize(poses.purple.mouth.path);
assert.equal(purpleMouth.at(-2) - purpleMouth[0], 18, "purple keeps a short smile");
assert.equal(
  windowMs,
  960,
  "this PR does not lengthen the existing authentication feedback window",
);
assert.deepEqual(sample(0), { blend: 0, complete: false });
assert.deepEqual(sample(-10), sample(0));
assert.deepEqual(sample(NaN), sample(0));
assert.equal(sample(100).blend, 0.5);
assert.equal(sample(200).complete, true);
assert.equal(sample(200, true).complete, false);
assert.equal(sample(280, true).complete, true);
assert.equal(sample(Infinity).blend, 1);
assert(sample(windowMs).complete);

const path = (d, filled, width = 0, purple = false) => ({
  points: normalize(d, purple),
  opacity: 1,
  fill: "#171717",
  fillOpacity: filled ? 1 : 0,
  stroke: "#171717",
  strokeOpacity: filled ? 0 : 1,
  strokeWidth: width,
});
const ellipse = (eye, radius, opacity) => ({
  matrix: [radius, 0, 0, radius, eye.x, eye.y],
  opacity,
});
const target = Object.entries(poses).map(([name, pose]) => ({
  body: path(pose.body, true, 0, name === "purple"),
  eyes: pose.eyes.map((eye) => ({
    white: ellipse(eye, eye.radius, eye.pupilRadius === undefined ? 0 : 1),
    pupil: ellipse(eye, eye.pupilRadius ?? eye.radius, eye.lid ? 0 : 1),
    lid: eye.lid ? [path(eye.lid, false, 4)] : [],
  })),
  mouths: pose.mouth ? [path(pose.mouth.path, pose.mouth.filled, pose.mouth.width)] : [],
}));
// Include left/right source faces and interrupted half-blinks without a neutral waypoint.
for (const direction of [-1, 1]) {
  const source = structuredClone(target);
  for (const pose of source) {
    for (const eye of pose.eyes) {
      for (const part of [eye.white, eye.pupil]) {
        part.matrix[4] += direction * 40;
        part.matrix[3] *= 0.4;
      }
    }
  }
  source[2].mouths = [path(direction < 0 ? "M435 400H495" : "M550 400H610", false, 4)];
  const saved = JSON.stringify([source, target]);
  assert.equal(mix(source, target, 0), source);
  assert.equal(mix(source, target, 1), target);
  for (let time = 0; time <= 200; time += 2) {
    const frame = mix(source, target, sample(time).blend);
    frame.forEach((pose, i) => {
      assert.equal(pose.body.points[1], 550);
      assert(pose.body.points.every(Number.isFinite));
      pose.eyes.forEach((eye, j) => {
        const fromX = source[i].eyes[j].white.matrix[4];
        const toX = target[i].eyes[j].white.matrix[4];
        assert(eye.white.matrix[4] >= Math.min(fromX, toX));
        assert(eye.white.matrix[4] <= Math.max(fromX, toX));
        assert(eye.white.matrix.every(Number.isFinite));
      });
    });
  }
  assert.equal(JSON.stringify([source, target]), saved, "neither endpoint is mutated");
  const interrupted = mix(source, target, sample(80).blend);
  assert.equal(mix(interrupted, target, 0), interrupted, "retry starts at the exact last drawing");
  const liveTarget = (time) => {
    const live = structuredClone(source);
    live[2].eyes[0].pupil.matrix[4] += 25 * time;
    return mix(target, live, time * time * (3 - 2 * time));
  };
  const h = 0.00001;
  const velocity =
    (liveTarget(1)[2].eyes[0].pupil.matrix[4] - liveTarget(1 - h)[2].eyes[0].pupil.matrix[4]) / h;
  assert(Math.abs(velocity - 25) < 0.02, "handoff error recovery meets the live pointer velocity");
}
const css = readFileSync(
  new URL("../apps/web/app/login/login-characters.css", import.meta.url),
  "utf8",
);
assert(!css.includes("yak-login-result-success"), "old bounce/exit keyframes stay removed");
assert(!css.includes("scaleY(0.55)"), "success never flattens all four eye groups");
assert(!css.includes("translateY(260px)"), "success no longer disappears below the ground");
const controller = readFileSync(
  new URL("../apps/web/app/login/login-success-transition.ts", import.meta.url),
  "utf8",
);
assert(
  !/requestAnimationFrame|setTimeout|setInterval/.test(controller),
  "the scene owns the only animation clock",
);
console.log(
  "Login success geometry checks passed (four authored faces, shared bodies, timing, live-pose handoff, interruption, direct recovery, legacy cleanup). Browser and real authentication acceptance are separate.",
);
