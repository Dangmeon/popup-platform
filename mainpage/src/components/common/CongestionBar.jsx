import React from 'react'

export default function CongestionBar({ reserved, capacity }){
  const pct = capacity > 0 ? Math.min(100, Math.round((reserved / capacity) * 100)) : 0
  return (
    <div className="congestion-bar">
      <div className="congestion-fill" style={{ width: `${pct}%` }} />
    </div>
  )
}
