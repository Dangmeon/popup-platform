import React, { useState } from 'react'
import { useApp } from '../../context/AppContext.jsx'

export default function BrandDashboard(){
  const app = useApp()
  const [tab, setTab] = useState('popups')

  const [form, setForm] = useState({ name:'', category:'패션', location:'', startDate:'', endDate:'', capacity:50 })
  const [goodsForm, setGoodsForm] = useState({ name:'', price:0, stockOnline:0 })

  function submitPopup(e){
    e.preventDefault()
    app.addPopup({ ...form, image:'https://picsum.photos/seed/new'+Date.now()+'/600/750' })
    setForm({ name:'', category:'패션', location:'', startDate:'', endDate:'', capacity:50 })
  }
  function submitGoods(e){
    e.preventDefault()
    app.addGoods({ ...goodsForm, price:Number(goodsForm.price), stockOnline:Number(goodsForm.stockOnline), image:'https://picsum.photos/seed/g'+Date.now()+'/500/500' })
    setGoodsForm({ name:'', price:0, stockOnline:0 })
  }

  return (
    <div className="container" style={{padding:'32px 24px 60px'}}>
      <h2 style={{fontSize:22,fontWeight:800,marginBottom:20}}>브랜드 관리자 대시보드</h2>
      <div className="tabs">
        <button className={tab==='popups'?'active':''} onClick={()=>setTab('popups')}>팝업 관리</button>
        <button className={tab==='goods'?'active':''} onClick={()=>setTab('goods')}>굿즈 관리</button>
      </div>

      {tab==='popups' && (
        <>
          <form onSubmit={submitPopup} className="card" style={{padding:20, display:'grid', gap:10, marginBottom:24}}>
            <input className="login-input" placeholder="팝업명" value={form.name} onChange={e=>setForm({...form,name:e.target.value})} required />
            <input className="login-input" placeholder="위치 (예: 서울 성동구)" value={form.location} onChange={e=>setForm({...form,location:e.target.value})} required />
            <div style={{display:'flex',gap:8}}>
              <input className="login-input" type="date" value={form.startDate} onChange={e=>setForm({...form,startDate:e.target.value})} required />
              <input className="login-input" type="date" value={form.endDate} onChange={e=>setForm({...form,endDate:e.target.value})} required />
            </div>
            <input className="login-input" type="number" placeholder="타임슬롯 수용인원" value={form.capacity} onChange={e=>setForm({...form,capacity:e.target.value})} />
            <button className="btn btn-primary" type="submit">팝업 등록</button>
          </form>

          <table>
            <thead><tr><th>팝업명</th><th>위치</th><th>기간</th><th>상태</th><th></th></tr></thead>
            <tbody>
              {app.popups.map(p => (
                <tr key={p.id}>
                  <td>{p.name}</td><td>{p.location}</td>
                  <td>{p.startDate} ~ {p.endDate}</td><td>{p.status}</td>
                  <td><button className="btn btn-sm btn-outline" onClick={()=>app.deletePopup(p.id)}>삭제</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {tab==='goods' && (
        <>
          <form onSubmit={submitGoods} className="card" style={{padding:20, display:'grid', gap:10, marginBottom:24}}>
            <input className="login-input" placeholder="상품명" value={goodsForm.name} onChange={e=>setGoodsForm({...goodsForm,name:e.target.value})} required />
            <input className="login-input" type="number" placeholder="가격" value={goodsForm.price} onChange={e=>setGoodsForm({...goodsForm,price:e.target.value})} required />
            <input className="login-input" type="number" placeholder="온라인 재고" value={goodsForm.stockOnline} onChange={e=>setGoodsForm({...goodsForm,stockOnline:e.target.value})} />
            <button className="btn btn-primary" type="submit">굿즈 등록</button>
          </form>

          <table>
            <thead><tr><th>상품명</th><th>가격</th><th>재고</th><th></th></tr></thead>
            <tbody>
              {app.goods.map(g => (
                <tr key={g.id}>
                  <td>{g.name}</td><td>{g.price.toLocaleString()}원</td><td>{g.stockOnline}</td>
                  <td><button className="btn btn-sm btn-outline" onClick={()=>app.deleteGoods(g.id)}>삭제</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}
