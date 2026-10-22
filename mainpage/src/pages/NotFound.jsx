import React from 'react'
import { Link } from 'react-router-dom'

export default function NotFound(){
  return (
    <div className="container empty-state error-page">
      <img src="/mascot-404.png" alt="길 잃은 너구리" className="error-mascot" />
      <h2 className="error-title">앗, 이 페이지는 없어요</h2>
      <p className="card-sub">주소를 잘못 입력했거나, 삭제된 페이지일 수 있어요.</p>
      <Link to="/" className="btn btn-primary" style={{ marginTop:20, display:'inline-block' }}>
        홈으로 돌아가기
      </Link>
    </div>
  )
}
