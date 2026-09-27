import { useEffect, useRef, useState } from "react";

import LoginPanel from "./LoginPanel";
import { CharactersScene, type ActionType, type FocusedField } from "./LoginScene";
import "./login.css";

interface LoginPageProps {
  onAuthenticated: () => Promise<void>;
}

type Pt = { x: number; y: number };

function clamp(n: number, min: number, max: number) {
  return Math.max(min, Math.min(max, n));
}

function lerp(a: number, b: number, t: number) {
  return a + (b - a) * t;
}

export default function LoginPage({ onAuthenticated }: LoginPageProps) {
  const stageRef = useRef<HTMLDivElement | null>(null);
  const bootStartRef = useRef(0);
  const mouseTargetRef = useRef<Pt>({ x: 0, y: 0 });
  const tiltTargetRef = useRef(0);

  const [mouse, setMouse] = useState<Pt>({ x: 0, y: 0 });
  const [action, setAction] = useState<{ type: ActionType; nonce: number }>({
    type: "BLINK",
    nonce: 0,
  });
  const [globalTilt, setGlobalTilt] = useState(0);
  const [bootT, setBootT] = useState(0);
  const [focusedField, setFocusedField] = useState<FocusedField>(null);
  const [stageRect, setStageRect] = useState({
    left: 0,
    top: 0,
    width: 0,
    height: 0,
  });

  useEffect(() => {
    bootStartRef.current = performance.now();
    let frame = 0;

    const tick = () => {
      setBootT(performance.now() - bootStartRef.current);
      frame = requestAnimationFrame(tick);
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, []);

  useEffect(() => {
    const onMove = (event: MouseEvent) => {
      mouseTargetRef.current = { x: event.clientX, y: event.clientY };

      const rect = stageRef.current?.getBoundingClientRect();
      if (!rect) return;

      const centerX = rect.left + rect.width / 2;
      const distanceX = (event.clientX - centerX) / (rect.width / 2);
      tiltTargetRef.current = clamp(distanceX, -1, 1) * 6;
    };

    window.addEventListener("mousemove", onMove, { passive: true });
    let frame = 0;

    const animate = () => {
      setMouse((current) => ({
        x: lerp(current.x, mouseTargetRef.current.x, 0.22),
        y: lerp(current.y, mouseTargetRef.current.y, 0.22),
      }));
      setGlobalTilt((current) => lerp(current, tiltTargetRef.current, 0.14));
      frame = requestAnimationFrame(animate);
    };

    frame = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("mousemove", onMove);
      cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const element = stageRef.current;
    if (!element) return;

    const update = () => {
      const rect = element.getBoundingClientRect();
      setStageRect({
        left: rect.left,
        top: rect.top,
        width: rect.width,
        height: rect.height,
      });
    };

    const observer = new ResizeObserver(update);
    observer.observe(element);
    update();

    window.addEventListener("scroll", update, true);
    window.addEventListener("resize", update);

    return () => {
      observer.disconnect();
      window.removeEventListener("scroll", update, true);
      window.removeEventListener("resize", update);
    };
  }, []);

  const fire = (type: ActionType) => {
    setAction({ type, nonce: Date.now() });
  };

  return (
    <main className="min-h-screen bg-white md:grid md:grid-cols-[7fr_5fr]">
      <section className="hidden min-h-screen overflow-hidden md:block">
        <CharactersScene
          ref={stageRef}
          mouse={mouse}
          action={action}
          globalTilt={globalTilt}
          bootT={bootT}
          stageW={stageRect.width}
          stageH={stageRect.height}
          stageRect={stageRect}
          focusedField={focusedField}
        />
      </section>

      <section className="relative flex min-h-screen items-center justify-center bg-white px-6 py-12 sm:px-10 lg:px-14">
        <div className="w-full max-w-[380px]">
          <div className="mb-8 text-center">
            <h1 className="m-0 text-[32px] font-bold leading-[1.15] tracking-[-0.025em] text-[#0f172a]">
              Welcome back!
            </h1>
            <p className="mt-2.5 text-[14px] text-[#64748b]">Please enter your details</p>
          </div>

          <LoginPanel
            onAuthenticated={onAuthenticated}
            onFire={fire}
            onFieldFocusChange={setFocusedField}
          />
        </div>
      </section>
    </main>
  );
}
