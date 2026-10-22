/*import React, { useMemo } from 'react'

export default function Snowfall({ count = 40 }){
  const flakes = useMemo(() => {
    return Array.from({ length: count }).map((_, i) => ({
      id: i,
      left: Math.random() * 100,
      size: 6 + Math.random() * 10,
      duration: 8 + Math.random() * 10,
      delay: Math.random() * 10,
      drift: (Math.random() * 80 - 40) + 'px',
      opacity: 0.4 + Math.random() * 0.6
    }))
  }, [count])

  return (
    <div className="snow-layer" aria-hidden="true">
      {flakes.map(f => (
        <span
          key={f.id}
          className="snowflake"
          style={{
            left: f.left + '%',
            fontSize: f.size + 'px',
            animationDuration: f.duration + 's',
            animationDelay: f.delay + 's',
            opacity: f.opacity,
            '--drift': f.drift
          }}
        >
          ❄
        </span>
      ))}
    </div>
  )
}
*/