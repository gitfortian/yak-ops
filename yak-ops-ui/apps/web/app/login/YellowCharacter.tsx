import { forwardRef, useCallback, useImperativeHandle, useLayoutEffect, useRef } from "react";

import type { LoginSceneState } from "./login-interaction";

const YELLOW_BODY_PATH =
  "M450 550V407C450 352 481 318 524 318C565 318 590 349 590 404C590 456 590 505 590 550Z";
const FACE_CENTER = { x: 520, y: 388 };
const REST_POSE: YellowFacePose = { offsetX: 0, offsetY: 0, turn: 0, reveal: 0 };

type YellowFacePose = { offsetX: number; offsetY: number; turn: number; reveal: number };
export type YellowPointer = { x: number; y: number };
export interface YellowCharacterHandle {
  update: (
    pointer: YellowPointer | null,
    state: LoginSceneState,
    deltaMs: number,
    inputMix: number,
  ) => void;
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

// A fixed SVG reference, never the moving eye or the animated character's bounding box.
function toSvgPointer(svg: SVGSVGElement | null, pointer: YellowPointer | null) {
  if (!svg || !pointer || !svg.getClientRects().length) return null;
  const matrix = svg.getScreenCTM();
  if (!matrix || !Number.isFinite(matrix.a * matrix.d - matrix.b * matrix.c)) return null;
  if (Math.abs(matrix.a * matrix.d - matrix.b * matrix.c) < 1e-8) return null;
  const point = new DOMPoint(pointer.x, pointer.y).matrixTransform(matrix.inverse());
  return Number.isFinite(point.x) && Number.isFinite(point.y) ? point : null;
}

// turn is a left/right pose coordinate, not a rotation of the whole face.
// The neutral band works in both upper and lower half-planes, without angle wrapping.
function resolveFaceTarget(state: LoginSceneState, pointer: YellowPointer | null): YellowFacePose {
  if (state === "passwordVisible") {
    return { offsetX: 0, offsetY: 0, turn: -1, reveal: 1 };
  }
  if (state !== "idle" || !pointer) return REST_POSE;

  const dx = pointer.x - FACE_CENTER.x;
  const dy = pointer.y - FACE_CENTER.y;
  if (!Number.isFinite(dx) || !Number.isFinite(dy)) return REST_POSE;
  const direction = dx / Math.hypot(dx, dy, 80);
  const progress = clamp((Math.abs(direction) - 0.06) / 0.34, 0, 1);
  return {
    offsetX: (dx / Math.hypot(dx, 180)) * 16,
    offsetY: (dy / Math.hypot(dy, 180)) * 22,
    turn: Math.sign(direction) * progress * progress * (3 - 2 * progress),
    reveal: 0,
  };
}

// All channels share a time constant. No independent eye/mouth pointer lag.
function stepFacePose(
  current: YellowFacePose,
  target: YellowFacePose,
  deltaMs: number,
  settleInput = false,
) {
  const elapsed = Number.isFinite(deltaMs) ? clamp(deltaMs, 0, 64) : 0;
  const alpha = -Math.expm1(-elapsed / 125);
  const next = {
    offsetX: current.offsetX + (target.offsetX - current.offsetX) * alpha,
    offsetY: current.offsetY + (target.offsetY - current.offsetY) * alpha,
    turn: current.turn + (target.turn - current.turn) * alpha,
    reveal: current.reveal + (target.reveal - current.reveal) * alpha,
  };
  // A held reference pose has exact endpoints, including at fractional pixel scales.
  if (
    (target.reveal === 1 || settleInput) &&
    Math.max(
      Math.abs(next.offsetX - target.offsetX),
      Math.abs(next.offsetY - target.offsetY),
      Math.abs(next.turn - target.turn),
      Math.abs(next.reveal - target.reveal),
    ) < 0.001
  ) {
    return target;
  }
  return next;
}

function faceGeometry(pose: YellowFacePose, inputMix = 0) {
  const turn = clamp(pose.turn, -1, 1);
  const reveal = clamp(pose.reveal, 0, 1);
  const input = clamp(inputMix, 0, 1) * (1 - reveal);
  const eyeX =
    ((FACE_CENTER.x - turn * 22) * (1 - reveal) + 510 * reveal) * (1 - input) + 554 * input;
  const mouthX =
    ((FACE_CENTER.x + turn * 36) * (1 - reveal) + 480 * reveal) * (1 - input) + 579 * input;
  const halfWidth = (40 - reveal * 10) * (1 - input) + 25 * input;
  const mouthY = (411 - reveal * 11) * (1 - input) + 400 * input;
  // Constrain the entire translation, not the eye alone, to preserve face cohesion.
  const offsetX = clamp(
    pose.offsetX * (1 - input),
    Math.max(-16, 480 - eyeX),
    Math.min(16, 560 - eyeX),
  );
  const offsetY = clamp(pose.offsetY * (1 - input), -22, 22);
  return {
    transform: `translate(${offsetX.toFixed(3)} ${offsetY.toFixed(3)})`,
    eyeX: eyeX.toFixed(3),
    mouth: `M${mouthX - halfWidth} ${mouthY}H${mouthX + halfWidth}`,
    success: `M${mouthX - 40} 407Q${mouthX} 425 ${mouthX + 40} 407`,
    failure: `M${mouthX - 40} 417Q${mouthX} 401 ${mouthX + 40} 417`,
  };
}

const REST_FACE = faceGeometry(REST_POSE);

export default forwardRef<YellowCharacterHandle, { sceneState: LoginSceneState }>(
  function YellowCharacter({ sceneState }, ref) {
    const rootRef = useRef<SVGGElement | null>(null);
    const faceRef = useRef<SVGGElement | null>(null);
    const eyeRef = useRef<SVGCircleElement | null>(null);
    const mouthRef = useRef<SVGPathElement | null>(null);
    const successRef = useRef<SVGPathElement | null>(null);
    const failureRef = useRef<SVGPathElement | null>(null);
    const poseRef = useRef<YellowFacePose>(REST_POSE);
    const reducedMotionRef = useRef<MediaQueryList | null>(null);

    const paint = useCallback((pose: YellowFacePose, inputMix = 0) => {
      const geometry = faceGeometry(pose, inputMix);
      faceRef.current?.setAttribute("transform", geometry.transform);
      eyeRef.current?.setAttribute("cx", geometry.eyeX);
      mouthRef.current?.setAttribute("d", geometry.mouth);
      successRef.current?.setAttribute("d", geometry.success);
      failureRef.current?.setAttribute("d", geometry.failure);
    }, []);

    useImperativeHandle(
      ref,
      () => ({
        update(pointer, state, deltaMs, inputMix) {
          if (reducedMotionRef.current?.matches) return;
          // Use the root SVG's matrix: entrance/result and face transforms are not input.
          const point =
            state === "idle"
              ? toSvgPointer(rootRef.current?.ownerSVGElement ?? null, pointer)
              : null;
          const target = resolveFaceTarget(state, point);
          poseRef.current = stepFacePose(poseRef.current, target, deltaMs, state === "inputFocus");
          paint(poseRef.current, inputMix);
        },
      }),
      [paint],
    );

    useLayoutEffect(() => {
      const media = window.matchMedia("(prefers-reduced-motion: reduce)");
      reducedMotionRef.current = media;
      const resetReducedPose = () => {
        if (!media.matches) return;
        poseRef.current = resolveFaceTarget(sceneState, null);
        paint(poseRef.current, sceneState === "inputFocus" ? 1 : 0);
      };
      resetReducedPose();
      media.addEventListener("change", resetReducedPose);
      return () => media.removeEventListener("change", resetReducedPose);
    }, [sceneState, paint]);

    return (
      <g
        ref={rootRef}
        data-character="yellow"
        className="yak-login-character yak-login-character--yellow"
      >
        <g className="yak-login-character--yellow__entry">
          <g className="yak-login-character--yellow__result">
            <g className="yak-login-character--yellow__focus">
              <path data-yellow-body-path d={YELLOW_BODY_PATH} fill="#F3D30B" />
              <g className="yak-login-character--yellow__input-face-pose">
                <g
                  ref={faceRef}
                  className="yak-login-character--yellow__face"
                  transform={REST_FACE.transform}
                >
                  <g className="yak-login-character--yellow__result-eyes">
                    <g className="yak-login-character--yellow__focus-eyes">
                      <circle
                        ref={eyeRef}
                        data-yellow-eye
                        cx={REST_FACE.eyeX}
                        cy="376"
                        r="5.4"
                        fill="#171717"
                      />
                    </g>
                  </g>
                  <path
                    ref={mouthRef}
                    className="yak-login-character__mouth yak-login-character__mouth--default"
                    d={REST_FACE.mouth}
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    ref={successRef}
                    className="yak-login-character__mouth yak-login-character__mouth--success"
                    d={REST_FACE.success}
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    ref={failureRef}
                    className="yak-login-character__mouth yak-login-character__mouth--failure"
                    d={REST_FACE.failure}
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                </g>
              </g>
            </g>
          </g>
        </g>
      </g>
    );
  },
);
