import React from 'react'
import { Link } from 'react-router-dom'

export default function ErrorPage(){
  return (
    <div className="container empty-state error-page">
      <img src="/mascot-error.png" alt="놀란 너구리" className="error-mascot error-mascot-shake" />
      <h2 className="error-title">앗, 문제가 생겼어요</h2>
      <p className="card-sub">일시적인 오류예요. 새로고침하거나 잠시 후 다시 시도해주세요.</p>
      <div style={{ display:'flex', gap:10, justifyContent:'center', marginTop:20 }}>
        <button className="btn btn-outline" onClick={()=>window.location.reload()}>새로고침</button>
        <Link to="/" className="btn btn-primary">홈으로 돌아가기</Link>
      </div>
    </div>
  )
}
