import { useId } from "react";

import { LOGIN_SUCCESS_POSES, type SuccessCharacter } from "./login-success-pose";

/** An endpoint, not a motion layer. It stays measurable outside the ordinary character rig. */
export default function LoginSuccessPose({ character }: { character: SuccessCharacter }) {
  const id = useId();
  const pose = LOGIN_SUCCESS_POSES[character];
  return (
    <>
      <g className="yak-login-character__success-pose" data-success-pose={character}>
        <path data-success-body d={pose.body} fill={pose.fill} />
        {pose.eyes.map((eye, index) => (
          <g key={index}>
            {eye.pupilRadius !== undefined && (
              <defs>
                <clipPath id={`${id}-${index}`} clipPathUnits="userSpaceOnUse">
                  <circle cx={eye.x} cy={eye.y} r={eye.radius} />
                </clipPath>
              </defs>
            )}
            <circle
              data-pose-eye="success"
              cx={eye.x}
              cy={eye.y}
              r={eye.radius}
              fill={eye.pupilRadius === undefined ? "#171717" : "#FFFFFF"}
              opacity={eye.lid ? 0 : 1}
            />
            {eye.pupilRadius !== undefined && (
              <g clipPath={`url(#${id}-${index})`}>
                <circle
                  data-pose-pupil="success"
                  cx={eye.x}
                  cy={eye.y}
                  r={eye.pupilRadius}
                  fill="#171717"
                />
              </g>
            )}
            {eye.lid && (
              <path
                data-success-lid
                d={eye.lid}
                fill="none"
                stroke="#171717"
                strokeWidth="4"
                strokeLinecap="round"
              />
            )}
          </g>
        ))}
        {pose.mouth && (
          <path
            data-success-mouth
            d={pose.mouth.path}
            fill={pose.mouth.filled ? "#171717" : "none"}
            stroke={pose.mouth.filled ? "none" : "#171717"}
            strokeWidth={pose.mouth.width}
            strokeLinecap="round"
          />
        )}
      </g>
      <g data-success-bridge />
    </>
  );
}
