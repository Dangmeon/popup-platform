import React, { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'

export default function MyPage(){
  const app = useApp()
  const [tab, setTab] = useState('reservations')

  if(!app.currentUser){
    return (
      <div className="container empty-state">
        <p>로그인이 필요합니다.</p>
        <Link to="/login" className="btn btn-primary" style={{ marginTop:16, display:'inline-block' }}>
          로그인하기
        </Link>
      </div>
    )
  }

  return (
    <div className="container">
      <div className="page-title">마이페이지</div>
      <p className="card-sub">{app.currentUser.name}님 ({app.currentUser.email})</p>

      <div className="tabs" style={{marginTop:24}}>
        <button className={'tab-btn'+(tab==='reservations'?' active':'')} onClick={()=>setTab('reservations')}>내 예약</button>
        <button className={'tab-btn'+(tab==='waitings'?' active':'')} onClick={()=>setTab('waitings')}>내 웨이팅</button>
      </div>

      {tab==='reservations' && (
        app.reservations.length===0 ? <div className="empty-state">예약 내역이 없어요.</div> :
        app.reservations.map(r => {
          const popup = app.popups.find(p=>p.id===r.popupId)
          return (
            <div className="cart-row" key={r.id}>
              <img src={popup?.image} alt="" />
              <div style={{flex:1}}>
                <div className="card-title">{popup?.title}</div>
                <div className="card-sub">{r.peopleCount}명 · 상태: {r.status}</div>
              </div>
              {r.status==='HOLD' && <button className="btn btn-outline" onClick={()=>app.cancelReservation(r.id)}>취소</button>}
            </div>
          )
        })
      )}

      {tab==='waitings' && (
        app.waitings.length===0 ? <div className="empty-state">웨이팅 내역이 없어요.</div> :
        app.waitings.map(w => {
          const popup = app.popups.find(p=>p.id===w.popupId)
          return (
            <div className="cart-row" key={w.id}>
              <img src={popup?.image} alt="" />
              <div style={{flex:1}}>
                <div className="card-title">{popup?.title}</div>
                <div className="card-sub">대기번호 {w.queueNumber}번 · 상태: {w.status}</div>
              </div>
              {w.status==='WAITING' && <button className="btn btn-outline" onClick={()=>app.cancelWaiting(w.id)}>취소</button>}
            </div>
          )
        })
      )}
    </div>
  )
}
