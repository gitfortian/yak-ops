function PurpleCharacter() {
  return (
    <g data-character="purple">
      <path d="M212 550V112Q212 102 222 102H394Q404 102 404 112V550Z" fill="#6128F5" />
      <g>
        <circle cx="269" cy="142" r="8.5" fill="#FFFFFF" />
        <circle cx="269" cy="142" r="3.5" fill="#171717" />
        <circle cx="337" cy="142" r="8.5" fill="#FFFFFF" />
        <circle cx="337" cy="142" r="3.5" fill="#171717" />
        <path
          d="M292 172Q303 177 314 172"
          fill="none"
          stroke="#171717"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </g>
    </g>
  );
}

function BlackCharacter() {
  return (
    <g data-character="black">
      <path d="M342 550V250Q342 242 350 242H456Q464 242 464 250V550Z" fill="#191A20" />
      <g>
        <circle cx="378" cy="276" r="7.5" fill="#FFFFFF" />
        <circle cx="378" cy="276" r="3.2" fill="#171717" />
        <circle cx="426" cy="276" r="7.5" fill="#FFFFFF" />
        <circle cx="426" cy="276" r="3.2" fill="#171717" />
      </g>
    </g>
  );
}

function YellowCharacter() {
  return (
    <g data-character="yellow">
      <path d="M450 550V405C450 345 483 310 525 310C570 310 598 349 598 405V550Z" fill="#F3D30B" />
      <g>
        <circle cx="492" cy="373" r="4.2" fill="#171717" />
        <circle cx="535" cy="373" r="4.2" fill="#171717" />
        <path
          d="M488 410H539"
          fill="none"
          stroke="#171717"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </g>
    </g>
  );
}

function OrangeCharacter() {
  return (
    <g data-character="orange">
      <path d="M72 550C72 457 144 388 242 388C340 388 410 457 410 550Z" fill="#FF7D2A" />
      <g>
        <circle cx="192" cy="451" r="4.2" fill="#171717" />
        <circle cx="252" cy="451" r="4.2" fill="#171717" />
        <path
          d="M211 483Q222 488 233 483"
          fill="none"
          stroke="#171717"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </g>
    </g>
  );
}

export default function LoginCharacters() {
  return (
    <div
      className="flex min-h-screen items-end justify-center overflow-hidden bg-[#efedf2]"
      aria-hidden="true"
    >
      <svg
        className="h-auto w-[min(88%,820px)]"
        viewBox="0 0 720 580"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <PurpleCharacter />
        <YellowCharacter />
        <BlackCharacter />
        <OrangeCharacter />
      </svg>
    </div>
  );
}
