import React from 'react'
import { useApp } from '../context/AppContext.jsx'
import PopupCard from '../components/common/PopupCard.jsx'
import GoodsCard from '../components/common/GoodsCard.jsx'

export default function Wishlist(){
  const app = useApp()
  const likedPopupList = app.popups.filter(p => app.likedPopups.includes(p.id))
  const likedProductList = app.products.filter(p => app.likedProducts.includes(p.id))

  return (
    <div className="container">
      <div className="page-title">마이플리</div>

      <div className="section-title">찜한 팝업</div>
      {likedPopupList.length === 0 ? (
        <div className="empty-state">찜한 팝업이 없어요.</div>
      ) : (
        <div className="grid-4">{likedPopupList.map(p => <PopupCard key={p.id} popup={p} />)}</div>
      )}

      <div className="section-title">찜한 굿즈</div>
      {likedProductList.length === 0 ? (
        <div className="empty-state">찜한 굿즈가 없어요.</div>
      ) : (
        <div className="grid-4">{likedProductList.map(p => <GoodsCard key={p.id} item={p} />)}</div>
      )}
    </div>
  )
}
