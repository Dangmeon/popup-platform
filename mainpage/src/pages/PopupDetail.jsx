import React, { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useApp } from '../context/AppContext.jsx'
import CongestionBar from '../components/common/CongestionBar.jsx'
import GoodsCard from '../components/common/GoodsCard.jsx'

const TIME_SLOTS = ['11:00', '13:00', '15:00', '17:00']

export default function PopupDetail(){
  const { id } = useParams()
  const app = useApp()
  const popup = app.popups.find(p => String(p.id) === String(id))
  const [selectedTime, setSelectedTime] = useState(TIME_SLOTS[0])
  const [peopleCount, setPeopleCount] = useState(1)
  const [myReservationId, setMyReservationId] = useState(null)
  const [myWaitingId, setMyWaitingId] = useState(null)

  if(!popup){
    return <div className="container empty-state">존재하지 않는 팝업입니다. <Link to="/popups">목록으로</Link></div>
  }

  const liked = app.likedPopups.includes(popup.id)
  const popupGoods = app.products.filter(g => g.popupId === popup.id)
  const myReservation = app.reservations.find(r => r.id === myReservationId)
  const myWaiting = app.waitings.find(w => w.id === myWaitingId)

  function handleReserve(){
    if(!app.currentUser){ app.showToast('로그인이 필요합니다'); return }
    const id = app.createReservation(popup.id, selectedTime, peopleCount)
    setMyReservationId(id)
    app.showToast('예약이 임시 선점되었어요. 10분 내 확정해주세요')
  }

  function handleWaiting(){
    if(!app.currentUser){ app.showToast('로그인이 필요합니다'); return }
    const id = app.registerWaiting(popup.id, peopleCount)
    setMyWaitingId(id)
    app.showToast('현장 대기열에 등록되었어요')
  }

  return (
    <div className="container">
      <div style={{ display:'grid', gridTemplateColumns:'1fr 1fr', gap:32, marginTop:24 }}>
        <img src={popup.image} alt={popup.title} style={{ width:'100%', borderRadius:'var(--radius)', aspectRatio:'4/3', objectFit:'cover' }} />

        <div>
          <span className="badge">{popup.category}</span>
          <h1 style={{ fontSize:28, fontWeight:800, margin:'12px 0 8px' }}>{popup.title}</h1>
          <p className="card-sub">{popup.location}</p>
          <p className="card-sub">{popup.startDate} ~ {popup.endDate}</p>

          <button
            className="btn btn-outline"
            style={{ marginTop:12 }}
            onClick={()=>app.toggleLikePopup(popup.id)}
          >{liked ? '♥ 찜 완료' : '♡ 찜하기'} ({popup.likes + (liked?1:0)})</button>

          <div style={{ marginTop:24 }}>
            <div className="card-sub" style={{ marginBottom:6 }}>현재 혼잡도</div>
            <CongestionBar reserved={popup.reserved} capacity={popup.capacity} />
            <div className="card-sub">{popup.reserved} / {popup.capacity}명 예약</div>
          </div>

          {/* 예약 섹션 */}
          <div className="form-box" style={{ margin:'24px 0 0', maxWidth:'100%' }}>
            <h2 style={{ fontSize:18 }}>예약하기</h2>
            <div className="tabs">
              {TIME_SLOTS.map(t => (
                <button key={t} className={'tab-btn'+(selectedTime===t?' active':'')} onClick={()=>setSelectedTime(t)}>{t}</button>
              ))}
            </div>
            <div className="form-group">
              <label>인원수</label>
              <div className="qty-box">
                <button onClick={()=>setPeopleCount(c=>Math.max(1,c-1))}>-</button>
                <span>{peopleCount}명</span>
                <button onClick={()=>setPeopleCount(c=>c+1)}>+</button>
              </div>
            </div>

            {!myReservation ? (
              <button className="btn btn-primary btn-block" onClick={handleReserve}>예약 선점하기</button>
            ) : myReservation.status === 'HOLD' ? (
              <div>
                <p className="card-sub">선점됨 · 10분 내 확정해주세요</p>
                <button className="btn btn-primary btn-block" onClick={()=>app.confirmReservation(myReservation.id)}>예약 확정</button>
                <button className="btn btn-outline btn-block" style={{marginTop:8}} onClick={()=>app.cancelReservation(myReservation.id)}>취소</button>
              </div>
            ) : myReservation.status === 'CONFIRMED' ? (
              <p style={{ color:'var(--primary-dark)', fontWeight:700 }}>✅ 예약이 확정되었습니다</p>
            ) : (
              <p className="card-sub">예약이 취소되었습니다</p>
            )}
          </div>

          {/* 웨이팅 섹션 */}
          <div className="form-box" style={{ margin:'16px 0 0', maxWidth:'100%' }}>
            <h2 style={{ fontSize:18 }}>현장 대기 등록</h2>
            {!myWaiting ? (
              <button className="btn btn-outline btn-block" onClick={handleWaiting}>현장 대기 등록하기</button>
            ) : myWaiting.status === 'WAITING' ? (
              <div style={{ textAlign:'center' }}>
                <div style={{ fontSize:36, fontWeight:800, color:'var(--primary)' }}>{myWaiting.queueNumber}번</div>
                <p className="card-sub">체크인 코드: <strong>{myWaiting.checkinCode}</strong></p>
                <button className="btn btn-outline btn-block" style={{marginTop:12}} onClick={()=>app.cancelWaiting(myWaiting.id)}>대기 취소</button>
              </div>
            ) : (
              <p className="card-sub">대기가 취소되었습니다</p>
            )}
          </div>
        </div>
      </div>

      {popupGoods.length > 0 && (
        <>
          <div className="section-title" style={{ marginTop:48 }}>🛍️ 판매 굿즈</div>
          <div className="grid-4">
            {popupGoods.map(g => <GoodsCard key={g.id} item={g} />)}
          </div>
        </>
      )}
    </div>
  )
}
