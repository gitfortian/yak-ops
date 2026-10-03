import {
  forwardRef,
  useCallback,
  useId,
  useImperativeHandle,
  useLayoutEffect,
  useRef,
} from "react";

import type { LoginSceneState } from "./login-interaction";

import { LOGIN_ENTRANCE_START, YELLOW_BODY_PATH, type YellowEntrancePose } from "./login-entrance";
const FACE_CENTER = { x: 520, y: 388 };
const REST_POSE: YellowFacePose = { offsetX: 0, offsetY: 0, turn: 0, reveal: 0 };

type YellowFacePose = { offsetX: number; offsetY: number; turn: number; reveal: number };
export type YellowPointer = { x: number; y: number };
export interface YellowCharacterHandle {
  setEntrance: (pose: YellowEntrancePose | null) => void;
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
  };
}

const REST_FACE = faceGeometry(REST_POSE);

export default forwardRef<YellowCharacterHandle, { sceneState: LoginSceneState }>(
  function YellowCharacter({ sceneState }, ref) {
    const rootRef = useRef<SVGGElement | null>(null);
    const entranceClipId = useId();
    const clipPathRef = useRef<SVGPathElement | null>(null);
    const bodyRef = useRef<SVGPathElement | null>(null);
    const extraEyeRef = useRef<SVGCircleElement | null>(null);
    const inputMixRef = useRef(0);
    const faceRef = useRef<SVGGElement | null>(null);
    const eyeRef = useRef<SVGCircleElement | null>(null);
    const mouthRef = useRef<SVGPathElement | null>(null);
    const successRef = useRef<SVGPathElement | null>(null);
    const poseRef = useRef<YellowFacePose>(REST_POSE);
    const reducedMotionRef = useRef<MediaQueryList | null>(null);

    const paint = useCallback((pose: YellowFacePose, inputMix = 0) => {
      inputMixRef.current = inputMix;
      const geometry = faceGeometry(pose, inputMix);
      faceRef.current?.setAttribute("transform", geometry.transform);
      eyeRef.current?.setAttribute("cx", geometry.eyeX);
      mouthRef.current?.setAttribute("d", geometry.mouth);
      successRef.current?.setAttribute("d", geometry.success);
    }, []);

    useImperativeHandle(
      ref,
      () => ({
        setEntrance(entrance) {
          bodyRef.current?.setAttribute("d", entrance?.path ?? YELLOW_BODY_PATH);
          clipPathRef.current?.setAttribute("d", entrance?.path ?? YELLOW_BODY_PATH);
          eyeRef.current?.setAttribute("cy", String(entrance?.eyeY ?? 376));
          extraEyeRef.current?.setAttribute("opacity", String(entrance?.extraEyeOpacity ?? 0));
          if (!entrance) {
            paint(poseRef.current, inputMixRef.current);
            return;
          }
          faceRef.current?.setAttribute("transform", "translate(0 0)");
          eyeRef.current?.setAttribute("cx", String(entrance.eyeX));
          extraEyeRef.current?.setAttribute("cx", String(entrance.extraEyeX));
          extraEyeRef.current?.setAttribute("cy", String(entrance.eyeY));
          mouthRef.current?.setAttribute("d", entrance.mouth);
        },
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
        bodyRef.current?.setAttribute("d", YELLOW_BODY_PATH);
        eyeRef.current?.setAttribute("cy", "376");
        extraEyeRef.current?.setAttribute("opacity", "0");
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
        <g data-character-motion className="yak-login-character--yellow__entry">
          <g className="yak-login-character--yellow__result">
            <g className="yak-login-character--yellow__focus">
              <defs>
                <clipPath id={entranceClipId} clipPathUnits="userSpaceOnUse">
                  <path ref={clipPathRef} d={LOGIN_ENTRANCE_START.yellow.path} />
                </clipPath>
              </defs>
              <path
                ref={bodyRef}
                data-yellow-body-path
                d={LOGIN_ENTRANCE_START.yellow.path}
                fill="#F3D30B"
              />
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
                        cx={LOGIN_ENTRANCE_START.yellow.eyeX}
                        cy={LOGIN_ENTRANCE_START.yellow.eyeY}
                        r="5.4"
                        fill="#171717"
                      />
                      <circle
                        ref={extraEyeRef}
                        data-yellow-entrance-eye
                        clipPath={`url(#${entranceClipId})`}
                        cx={LOGIN_ENTRANCE_START.yellow.extraEyeX}
                        cy={LOGIN_ENTRANCE_START.yellow.eyeY}
                        r="5.4"
                        fill="#171717"
                        opacity="0"
                      />
                    </g>
                  </g>
                  <path
                    ref={mouthRef}
                    className="yak-login-character__mouth yak-login-character__mouth--default"
                    d={LOGIN_ENTRANCE_START.yellow.mouth}
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
                </g>
              </g>
            </g>
          </g>
        </g>
        <g className="yak-login-character__failure-pose" data-failure-pose="yellow">
          <path data-failure-body d={YELLOW_BODY_PATH} fill="#F3D30B" />
          <circle data-pose-eye="failure" cx="532" cy="388" r="5.4" fill="#171717" />
          <path
            data-failure-mouth
            d="M545 410C556 400 563 400 572 403S584 406 591 401"
            fill="none"
            stroke="#171717"
            strokeWidth="4"
            strokeLinecap="round"
          />
        </g>
      </g>
    );
  },
);
