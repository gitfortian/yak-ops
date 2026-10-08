import {
  BLACK_DEFAULT_BODY_PATH,
  buildOrangeBodyPath,
  PURPLE_DEFAULT_BODY_PATH,
  YELLOW_BODY_PATH,
} from "./login-entrance";

export type SuccessCharacter = "purple" | "black" | "yellow" | "orange";
type SuccessEye = { x: number; y: number; radius: number; pupilRadius?: number; lid?: string };
type SuccessPose = {
  body: string;
  fill: string;
  eyes: readonly SuccessEye[];
  mouth: { path: string; filled: boolean; width: number } | null;
};

// The ordinary orange smile and the successful one have one geometric source.
export const ORANGE_HAPPY_MOUTH_PATH =
  "M213 475Q213 473 215 473H246Q248 473 248 475C246.8 483.5 240 488 230.5 488C221 488 214.2 483.5 213 475Z";

export const LOGIN_SUCCESS_POSES: Record<SuccessCharacter, SuccessPose> = {
  purple: {
    body: PURPLE_DEFAULT_BODY_PATH,
    fill: "#6128F5",
    eyes: [
      { x: 274, y: 145, radius: 5.5, pupilRadius: 2.3 },
      { x: 335, y: 145, radius: 5.5, pupilRadius: 2.3 },
    ],
    mouth: { path: "M295.5 172Q304.5 178 313.5 172", filled: false, width: 3 },
  },
  black: {
    body: BLACK_DEFAULT_BODY_PATH,
    fill: "#191A20",
    eyes: [
      { x: 379, y: 278, radius: 8.5, pupilRadius: 3.4 },
      { x: 428, y: 278, radius: 8.5, pupilRadius: 3.4 },
    ],
    mouth: null,
  },
  yellow: {
    body: YELLOW_BODY_PATH,
    fill: "#F3D30B",
    eyes: [{ x: 548, y: 376, radius: 5.4 }],
    mouth: { path: "M563 411H607Q619 411 625 406", filled: false, width: 4 },
  },
  orange: {
    body: buildOrangeBodyPath(0, 0),
    fill: "#FF7D2A",
    eyes: [
      { x: 190, y: 460, radius: 8, lid: "M181 461Q190 452 199 461" },
      { x: 270, y: 460, radius: 8, lid: "M261 461Q270 452 279 461" },
    ],
    mouth: { path: ORANGE_HAPPY_MOUTH_PATH, filled: true, width: 0 },
  },
};
