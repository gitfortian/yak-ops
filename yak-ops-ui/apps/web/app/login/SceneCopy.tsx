import { useEffect, useState } from "react";

const COPY = [
  "Welcome To Baize Flow 🌊",
  "让数据优雅的流动起来 💫",
  "让流动更轻一点 ✨",
];

function CyclingCopy() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setIndex((current) => (current + 1) % COPY.length);
    }, 3200);

    return () => window.clearInterval(timer);
  }, []);

  return <span>{COPY[index]}</span>;
}

export default function SceneCopy() {
  return (
    <div className="scene-copy">
      <div className="scene-copy__badge">
        <CyclingCopy />
      </div>

      <h1 className="scene-copy__title">今天也一起元气满满 ✨</h1>
      <p className="scene-copy__desc">慢一点，让数据也有呼吸感 🍃</p>
    </div>
  );
}
