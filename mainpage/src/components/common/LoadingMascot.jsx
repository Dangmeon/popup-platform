import React from 'react'

export default function LoadingMascot({ text = '불러오는 중...' }){
  return (
    <div className="loading-wrap">
      <div className="sprite-stage">
        <div className="sprite-runner" />
        <div className="loading-shadow" />
      </div>
      <p className="loading-text">{text}</p>
    </div>
  )
}
